package ch.ddis.speakeasy.feedback

import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.require
import java.io.File
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock

typealias FormId = UID

/**
 * A class representing a feedback form.
 */
data class FeedbackForm(val formName: String, val requests: List<FeedbackRequest>) {
    init {
        // Validate form name as a filename
        // NOTE : This is TEMPORARY.
        // In the near future, we will use uuid as file names
        require<InvalidFormException>(formName.isNotBlank(), "Form name cannot be blank")
        require<InvalidFormException>(formName.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9_\\- ]*[a-zA-Z0-9]\$")), 
            "Form name must start and end with a letter or number, and can only contain letters, numbers, spaces, underscores, and hyphens")
        require<InvalidFormException>(formName.length <= 100, "Form name cannot be longer than 100 characters")
        require<InvalidFormException>(!formName.contains(".."), "Form name cannot contain '..'")
        require<InvalidFormException>(!formName.contains("/"), "Form name cannot contain '/'")
        require<InvalidFormException>(!formName.contains("\\"), "Form name cannot contain '\\'")
        
        require<InvalidFormException>(requests.isNotEmpty(), "Form must contain at least one question")
        val shortnames = requests.map { it.shortname }
        require<InvalidFormException>(shortnames.size == shortnames.distinct().size, "Shortnames must be unique")
        // Check if the ids are increasing and startin from 0
        val ids = requests.map { it.id.toInt() }
        require<InvalidFormException>(ids.sorted() == ids, "Question IDs must be increasing")
        require<InvalidFormException>(ids.first() == 0, "Question IDs must start from 0")
    }
}
// TODO : id should be int. Kept that way for backwards compatibility
data class FeedbackRequest(val id: String, val type: String, val name: String, val shortname: String, val options: List<FeedbackAnswerOption>) {
    init {
        require<InvalidFormException>(id.toIntOrNull() != null, "Question ID must be an integer")
        require<InvalidFormException>(id.toInt() >= 0, "Question ID must be non-negative")
        require<InvalidFormException>(name.isNotBlank(), "Name cannot be blank")
        require<InvalidFormException>(shortname.isNotBlank(), "Shortname cannot be blank")
        if (type == "multiple") {
            require<InvalidFormException>(options.isNotEmpty(), "Multiple choice questions must have at least one option")
        }
    }
}
data class FeedbackAnswerOption(val name: String, val value: Int) {
    init {
        require<InvalidFormException>(name.isNotBlank(), "Option name cannot be blank")
    }
}


class InvalidFormException(message: String) : Exception(message)

object FormManager {
    private lateinit var formsPath: File
    private val kMapper: ObjectMapper = jacksonObjectMapper()

    // CopyOnWrite basically create a copy on each write operation.
    // Useful as there will be much more reads that writes here,
    // So it's easier to implement - no need for general locking - and still guaranteed to be thread safe
    private val forms = CopyOnWriteArrayList<FeedbackForm>()
    // Fine-grained threading. Multiple thread can read different files at the same time
    private val fileLocks = ConcurrentHashMap<String, ReentrantLock>()

    private fun getLockForForm(formName: String): ReentrantLock {
        return fileLocks.computeIfAbsent(formName) { ReentrantLock() }
    }

    fun init(config: Config) {
        // Throw if already initialized
        if (this::formsPath.isInitialized) {
            throw IllegalStateException("FormManager is already initialized")
        }
        this.formsPath = File(File(config.dataPath), "feedbackforms/")
        if (!this.formsPath.exists()) {
            this.formsPath.mkdirs()
            println("No feedback forms directory found at ${this.formsPath.absolutePath}, created it.")
        }

        this.formsPath
            .walk()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                println("Reading feedback form from ${file.name}")
                val feedbackForm: FeedbackForm = try {
                    kMapper.readValue(file)
                } catch (e: Exception) {
                    System.err.println("Error reading feedback form from ${file.name}: ${e.message}")
                    e.printStackTrace()
                    return@forEach
                }
                if (this.forms.none { it.formName == feedbackForm.formName }) {
                    this.forms.add(feedbackForm)
                } else {
                    System.err.println("formNames in feedbackforms should be unique  -> ignored duplicates ${feedbackForm.formName}.")
                }
            }
        this.forms.sortBy { it.formName } // Ensure that after each initialization, the forms are sorted in ascending order by formName

        print("Loaded feedback forms: ")
        this.forms.forEach { form ->
            print("${form.formName} ")
        }
    }


    /**
     * Creates a new feedback form and saves it to the forms directory.
     *
     * @param newForm The new feedback form to be created.
     * @throws IllegalArgumentException if a form with the same name already exists
     */
    fun createNewForm(newForm : FeedbackForm) {
        val formFile = File(formsPath, "${newForm.formName}.json")
        if (formFile.exists()) {
            throw IllegalArgumentException("A form with name '${newForm.formName}' already exists")
        }
        val lock = getLockForForm(newForm.formName)
        lock.lock()
        try {
            this.kMapper.writeValue(formFile, newForm)
            this.forms.add(newForm)
            this.forms.sortBy { it.formName }
        }
        finally {
            lock.unlock()
        }
    }

    /**
     * Delete a feedback form from the forms directory.
     * 
     * @param formName The name of the form to delete
     * @throws IllegalArgumentException if the form doesn't exist
     */
    fun deleteForm(formName: String) {
        val formFile = File(formsPath, "$formName.json")
        if (!formFile.exists()) {
            throw IllegalArgumentException("Form '$formName' not found")
        }
        val lock = getLockForForm(formName)

        lock.lock()
        try {
            formFile.delete()
            forms.removeIf { it.formName == formName }
        }
        finally {
            lock.unlock()
        }
    }

    /**
     * Update an existing feedback form, but thread safe :O
     *
     * @param formName The name of the form to update
     * @param newForm The new feedback form data
     * @throws IllegalArgumentException if the form doesn't exist
     */
    fun updateForm(formName: String, newForm: FeedbackForm) {
        val formFile = File(formsPath, "$formName.json")
        if (!formFile.exists()) {
            throw IllegalArgumentException("Form '$formName' not found")
        }
        val lock = getLockForForm(formName)

        lock.lock()
        try {
            this.kMapper.writeValue(formFile, newForm)
            forms.removeIf { it.formName == formName }
            forms.add(newForm)
            forms.sortBy { it.formName }
        }
        finally {
            lock.unlock()
        }
    }

    fun listForms(): List<FeedbackForm> {
        return this.forms // Note that this is already thread safe
    }

    @Deprecated("Prefer to use getForm and deal with any exception raised instead")
    fun isValidFormName(formName: String): Boolean {
        return this.forms.any { it.formName == formName }
    }

    /**
     * Gets a feedback form by name.
     *
     * @param formName The name of the form to retrieve
     * @return The feedback form
     * @throws IllegalArgumentException if the form doesn't exist
     */
    fun getForm(formName : String): FeedbackForm {
        return forms.find { it.formName == formName } 
            ?: throw IllegalArgumentException("Form '$formName' not found")
    }
}
