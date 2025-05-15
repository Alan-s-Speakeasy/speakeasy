import {Component, OnInit, AfterViewInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators, AbstractControl, ValidatorFn} from '@angular/forms';
import {AlertService} from "../alert";
import {FormService} from "../../../openapi";
import {FeedbackForm, FeedbackAnswerOption} from "../../../openapi";
import {HttpErrorResponse} from "@angular/common/http";
import {MatTooltip} from "@angular/material/tooltip";
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';

interface FormDefinition {
  id: string;
  name: string;
  questions: {
    type: 'text' | 'options';
    name: string;
    shortname: string;
    options?: FeedbackAnswerOption[];
  }[];
  lastModified: Date;
}

@Component({
  selector: 'app-form-definitions',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, MatTooltip, NgbAccordionModule],
  templateUrl: './form-definitions.component.html',
})
export class FormDefinitionsComponent implements OnInit {
  forms: FormDefinition[] = [];
  isLoading = false;

  /**
   * The currently selected form in the list.
   * Used to track which form is being viewed or edited.
   * - When a user clicks on a form in the list, this is set to that form
   * - When editing a form, this contains the form being edited
   * - When creating a new form, this is set to the new form draft
   * - When no form is selected, this is null
   */
  selectedForm: FormDefinition | null = null;

  /**
   * Flag indicating whether the form is in editing mode.
   * Used to control the UI state between view and edit modes.
   * - When true: Shows the form editor with input fields
   * - When false: Shows the form preview
   * - Set to true when:
   *   - Creating a new form (createNewForm)
   *   - Editing an existing form (startEditing)
   * - Set to false when:
   *   - Canceling edits (cancelEditing)
   *   - Successfully saving a form (saveForm)
   */
  isEditing = false;

  /**
   * The main form group that contains all form data.
   * Used to manage the form's data and validation state.
   * Contains:
   * - name: The form's name
   * - questions: An array of question form groups
   * Each question group contains:
   * - type: The question type (text/options)
   * - name: The question name
   * - shortname: The question's short identifier
   * - options: Array of options (for options type questions)
   */
  formGroup!: FormGroup;

  /**
   * The JSON string input for importing forms.
   * Used in the JSON import modal to store the pasted or uploaded JSON.
   * - Set when:
   *   - Opening the JSON modal with an existing form (openJsonModal)
   *   - Uploading a JSON file (onFileSelected)
   *   - Pasting JSON directly
   * - Cleared when:
   *   - Closing the JSON modal (closeJsonModal)
   *   - Successfully importing a form (importJson)
   */
  jsonInput: string = '';

  /**
   * Flag controlling the visibility of the JSON import modal.
   * Used to show/hide the modal for importing forms via JSON.
   * - Set to true when:
   *   - Clicking the import button (openJsonModal)
   * - Set to false when:
   *   - Clicking cancel (closeJsonModal)
   *   - Successfully importing a form (importJson)
   */
  showJsonModal = false;

  /**
   * Error message for JSON validation.
   * Used to display validation errors when importing JSON.
   * - Set when:
   *   - JSON parsing fails (importJson)
   *   - JSON structure is invalid (importJson)
   *   - Backend validation fails (importJson)
   * - Cleared when:
   *   - Opening the JSON modal (openJsonModal)
   *   - Closing the JSON modal (closeJsonModal)
   */
  jsonValidationError: string | null = null;

  questionTypes = [
    {value: 'text', label: 'Text Input'},
    {value: 'options', label: 'Options (Select/Radio)'}
  ];

  // Pre-registered options sets
  predefinedOptionsSets = [
    {
      name: 'Likert scale',
      options: [
        {name: 'Strongly Disagree', value: -2},
        {name: 'Slightly Disagree', value: -1},
        {name: 'Not Applicable / I don\'t know', value: 0},
        {name: 'Slightly Agree', value: 1},
        {name: 'Strongly Agree', value: 2}
      ]
    }
  ];

  showExportModal = false;
  exportJsonString: string = '';

  constructor(
    private fb: FormBuilder,
    private alertService: AlertService,
    private formService: FormService
  ) {
    this.formGroup = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(1), this.uniqueFormNameValidator()]],
      questions: this.fb.array([], [Validators.required, this.atLeastOneQuestion()])
    });
  }

  ngOnInit() {
    this.loadForms();
  }

  private loadForms() {
    this.isLoading = true;
    this.formService.getApiFeedbackforms().subscribe({
      next: (response: FeedbackForm[]) => {
        if (response) {
          this.forms = response.map(form => ({
            id: form.formName,
            name: form.formName,
            questions: form.requests.map(q => ({
              type: q.type as 'text' | 'options',
              name: q.name,
              shortname: q.shortname,
              options: q.options
            })),
            lastModified: new Date()
          }));
        } else {
          this.forms = [];
        }
        this.isLoading = false;
      },
      error: (error: Error) => {
        this.alertService.error('Failed to load forms: ' + error.message);
        this.isLoading = false;
      }
    });
  }

  get questions() {
    return this.formGroup.get('questions') as FormArray;
  }

  addQuestion() {
    const questionGroup = this.fb.group({
      type: ['text', Validators.required],
      name: ['', [Validators.required, Validators.minLength(3)]],
      shortname: ['', [Validators.required, Validators.pattern('^[a-zA-Z][a-zA-Z0-9_]*$'), Validators.minLength(2)]],
      options: this.fb.array([])
    });

    this.questions.push(questionGroup);
  }

  removeQuestion(index: number) {
    this.questions.removeAt(index);
  }

  getOptions(questionIndex: number) {
    return this.questions.at(questionIndex).get('options') as FormArray;
  }

  addOption(questionIndex: number) {
    const optionGroup = this.fb.group({
      name: ['', Validators.required],
      value: [0, Validators.required]
    });
    this.getOptions(questionIndex).push(optionGroup);
  }

  removeOption(questionIndex: number, optionIndex: number) {
    this.getOptions(questionIndex).removeAt(optionIndex);
  }

  addPredefinedOptions(questionIndex: number, optionsSetIndex: number) {
    if (optionsSetIndex === undefined || optionsSetIndex < 0) return;

    const optionsSet = this.predefinedOptionsSets[optionsSetIndex];
    const optionsArray = this.getOptions(questionIndex);

    // Clear existing options
    optionsArray.clear();

    // Add predefined options
    optionsSet.options.forEach(option => {
      const optionGroup = this.fb.group({
        name: [option.name, Validators.required],
        value: [option.value, Validators.required]
      });
      optionsArray.push(optionGroup);
    });
  }

  selectForm(form: FormDefinition) {
    this.selectedForm = form;
  }

  createNewForm() {
    // Create a new form with default values
    const newForm: FormDefinition = {
      id: Date.now().toString(),
      name: 'New Form',
      questions: [],
      lastModified: new Date()
    };

    // Add to the list
    this.forms.push(newForm);

    // Select and start editing
    this.selectedForm = newForm;
    this.isEditing = true;

    // Reset form group
    this.formGroup.reset({
      name: 'New Form',
      questions: []
    });
    this.questions.clear();
  }

  startEditing() {
    if (this.selectedForm) {
      // Reset the form group
      this.formGroup.reset();

      // Set the form name
      this.formGroup.patchValue({
        name: this.selectedForm.name
      });

      // Clear existing questions
      this.questions.clear();

      // Add each question from the selected form
      this.selectedForm.questions.forEach((q, index) => {
        const questionGroup = this.fb.group({
          type: [q.type, Validators.required],
          name: [q.name, Validators.required],
          shortname: [q.shortname, Validators.required],
          options: this.fb.array([])
        });

        // Add options if they exist
        if (q.options && q.options.length > 0) {
          const optionsArray = questionGroup.get('options') as FormArray;
          q.options.forEach(opt => {
            optionsArray.push(this.fb.group({
              name: [opt.name, Validators.required],
              value: [opt.value, Validators.required]
            }));
          });
        }

        this.questions.push(questionGroup);
      });

      // Enable editing mode
      this.isEditing = true;
    }
  }

  saveForm() {
    if (this.formGroup.valid && !this.hasEmptyOptionArrays()) {
      const formData = this.formGroup.value;
      const questions = formData.questions.map((q: any) => ({
        type: q.type,
        name: q.name,
        shortname: q.shortname,
        options: q.options && q.options.length > 0 ? q.options : []
      }));

      // Create the form payload for API
      const formToSave: FeedbackForm = {
        formName: formData.name,
        requests: questions.map((q: any, index: number) => ({
          id: index.toString(),
          type: q.type,
          name: q.name,
          shortname: q.shortname,
          options: q.options || []
        }))
      };

      // Determine if we're creating a new form or updating an existing one
      const isNewForm = !this.selectedForm ||
        (this.selectedForm && this.selectedForm.id !== formData.name);

      if (isNewForm) {
        // Create new form with POST
        this.formService.postApiFeedbackforms(formToSave).subscribe({
          next: (response) => {
            this.alertService.success('Form created successfully');
            this.loadForms();
            this.isEditing = false;
          },
          error: (error: HttpErrorResponse) => {
            this.alertService.error('Failed to create form: ' + error.error.description);
          }
        });
        // Keep the current form selected the one we created
        this.selectedForm = {
          id: formData.name,
          name: formData.name,
          questions: questions,
          lastModified: new Date()
        };
      } else {
        // Update existing form with PUT
        this.formService.putApiFeedbackformsByFormName(formData.name, formToSave).subscribe({
          next: (response) => {
            this.alertService.success('Form updated successfully');
            this.loadForms();
            this.isEditing = false;
          },
          error: (error: Error) => {
            console.error('Error updating form:', error);
            this.alertService.error('Failed to update form: ' + error.message);
          }
        });
      }
    } else {
      // Mark all fields as touched to trigger validation messages
      this.markFormGroupTouched(this.formGroup);

      // Show specific validation error messages
      if (this.formGroup.get('name')?.errors?.['required']) {
        this.alertService.error('Form title is required');
      } else if (this.formGroup.get('name')?.errors?.['minLength']) {
        this.alertService.error('Form title cannot be empty');
      } else if (this.questions.errors?.['atLeastOneQuestion']) {
        this.alertService.error('At least one question is required');
      }
    }
  }

  // Helper to mark all controls in a form group as touched
  markFormGroupTouched(formGroup: FormGroup) {
    Object.values(formGroup.controls).forEach(control => {
      control.markAsTouched();

      if (control instanceof FormGroup) {
        this.markFormGroupTouched(control);
      } else if (control instanceof FormArray) {
        for (let i = 0; i < control.length; i++) {
          if (control.at(i) instanceof FormGroup) {
            this.markFormGroupTouched(control.at(i) as FormGroup);
          } else {
            control.at(i).markAsTouched();
          }
        }
      }
    });
  }

  // Check if any question with type 'options' has an empty options array
  hasEmptyOptionArrays(): boolean {
    if (!this.questions) return false;

    for (let i = 0; i < this.questions.length; i++) {
      const question = this.questions.at(i);
      if (question.get('type')?.value === 'options' &&
        (question.get('options') as FormArray).length === 0) {
        return true;
      }
    }
    return false;
  }

  // Check if any question has validation errors
  hasInvalidQuestions(): boolean {
    if (!this.questions) return false;

    for (let i = 0; i < this.questions.length; i++) {
      const question = this.questions.at(i);
      if (question.invalid) {
        return true;
      }
      // Check if it's a text question with proper validation
      if (question.get('type')?.value === 'text') {
        const name = question.get('name');
        const shortname = question.get('shortname');

        if (name?.errors?.['minLength']) {
          return true;
        }
        if (shortname?.errors?.['pattern'] || shortname?.errors?.['minLength']) {
          return true;
        }
      }
    }
    return false;
  }

  getQuestionErrorMessage(question: AbstractControl, field: string): string {
    const control = question.get(field);
    if (!control?.errors) return '';

    if (field === 'name') {
      if (control.errors['required']) return 'Question name is required';
      if (control.errors['minLength']) return 'Question name must be at least 3 characters long';
    }

    if (field === 'shortname') {
      if (control.errors['required']) return 'Short name is required';
      if (control.errors['pattern']) return 'Short name must start with a letter and contain only letters, numbers, and underscores';
      if (control.errors['minLength']) return 'Short name must be at least 2 characters long';
    }

    return '';
  }

  getFormErrorMessage(field: string): string {
    const control = this.formGroup.get(field);
    if (!control?.errors) return '';

    if (field === 'name') {
      if (control.errors['required']) return 'Form name is required';
      if (control.errors['minLength']) return 'Form name cannot be empty';
      if (control.errors['duplicateName']) return 'A form with this name already exists';
    }

    return '';
  }

  deleteForm(form: FormDefinition) {
    // Alternative implementation if there was a delete endpoint:
    this.formService.deleteApiFeedbackformsByFormName(form.name).subscribe({
      next: () => {
        this.alertService.success('Form deleted successfully');
        this.forms = this.forms.filter(f => f.id !== form.id);
        if (this.selectedForm?.id === form.id) {
          this.selectedForm = null;
        }
      },
      error: (error: Error) => {
        this.alertService.error('Failed to delete form: ' + error.message);
      }
    });
  }

  // JSON operations
  openJsonModal() {
    this.showJsonModal = true;
    // Clear the JSON input when opening the modal
    this.jsonInput = '';
    this.jsonValidationError = null;
  }

  closeJsonModal() {
    this.showJsonModal = false;
    this.jsonInput = '';
    this.jsonValidationError = null;
  }

  onFileSelected(event: Event) {
    const fileInput = event.target as HTMLInputElement;
    if (fileInput.files && fileInput.files.length > 0) {
      const file = fileInput.files[0];

      // Only accept JSON files
      if (file.type !== 'application/json' && !file.name.endsWith('.json')) {
        this.alertService.error('Please select a valid JSON file');
        fileInput.value = '';
        return;
      }

      // Read the file content
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const content = reader.result as string;
          // Validate JSON format
          JSON.parse(content); // This will throw if content is not valid JSON
          this.jsonInput = content;
        } catch (error) {
          this.alertService.error('Invalid JSON file. Please check the file and try again.');
          this.jsonInput = '';
        }
      };
      reader.onerror = () => {
        this.alertService.error('Error reading file');
      };
      reader.readAsText(file);
    }
  }

  importJson() {
    if (!this.jsonInput.trim()) {
      this.alertService.error('Please provide JSON data to import');
      return;
    }

    // Clear any previous validation errors
    this.jsonValidationError = null;

    try {
      const formData = JSON.parse(this.jsonInput);

      // Validate the imported data has the required structure
      if (!formData.name || !formData.questions || !Array.isArray(formData.questions)) {
        throw new Error('Invalid form structure');
      }

      // Create a new form from the imported data
      const newForm: FeedbackForm = {
        formName: formData.name,
        requests: formData.questions.map((q: any, index: number) => ({
          id: index.toString(),
          type: q.type === 'options' ? 'options' : 'text',
          name: q.name,
          shortname: q.shortname,
          options: q.options ? q.options.map((opt: any) => ({
            name: opt.name,
            value: opt.value
          })) : []
        }))
      };

      // Save to backend
      this.formService.postApiFeedbackforms(newForm).subscribe({
        next: () => {
          this.alertService.success('Form imported successfully');
          this.loadForms();
          this.closeJsonModal();
        },
        error: (error: HttpErrorResponse) => {
          // Check if this is a validation error from InvalidFormException
          if (error.error && error.error.description) {
            this.jsonValidationError = `${error.error.description}`;
          } else {
            this.alertService.error('Failed to import form: ' + (error.error?.description || 'Unknown error'));
          }
        }
      });
    } catch (error) {
      this.jsonValidationError = "Invalid JSON format. Please check your input.";
    }
  }

  exportJson() {
    if (this.selectedForm) {
      const jsonStr = JSON.stringify(this.selectedForm, null, 2);

      // Create a blob and download link
      const blob = new Blob([jsonStr], {type: 'application/json'});
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${this.selectedForm.name.replace(/\s+/g, '_')}.json`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    }
  }

  // Update form name and description in real-time
  updateFormName() {
    if (this.selectedForm && this.formGroup.get('name')?.value) {
      const index = this.forms.findIndex(f => f.id === this.selectedForm?.id);
      if (index !== -1) {
        this.forms[index].name = this.formGroup.get('name')?.value;
      }
    }
  }

  // Remove updateFormDescription as description field is not part of our model

  // Custom validator to ensure at least one question exists
  private atLeastOneQuestion(): ValidatorFn {
    return (control: AbstractControl) => {
      const formArray = control as FormArray;
      if (formArray.length === 0) {
        return { atLeastOneQuestion: true };
      }
      return null;
    };
  }

  isQuestionInvalid(question: AbstractControl): boolean {
    if (question.invalid) return true;

    // Check name and shortname validation
    const name = question.get('name');
    const shortname = question.get('shortname');
    if (name?.invalid || shortname?.invalid) return true;

    // Check options validation if it's an options type question
    if (question.get('type')?.value === 'options') {
      const options = question.get('options') as FormArray;
      if (options.length === 0) return true;

      // Check if any option is invalid
      for (let i = 0; i < options.length; i++) {
        const option = options.at(i);
        if (option.invalid) return true;
      }
    }

    return false;
  }

  cancelEditing() {
    // If this is a new form (not saved yet), remove it from the list
    if (this.selectedForm && this.forms.includes(this.selectedForm)) {
      this.forms = this.forms.filter(f => f.id !== this.selectedForm?.id);
    }

    // Reset the form state
    this.isEditing = false;
    this.selectedForm = null;
    this.formGroup.reset();
  }

  openExportModal() {
    if (this.selectedForm) {
      this.exportJsonString = JSON.stringify(this.selectedForm, null, 2);
      this.showExportModal = true;
    }
  }

  closeExportModal() {
    this.showExportModal = false;
    this.exportJsonString = '';
  }

  copyToClipboard() {
    navigator.clipboard.writeText(this.exportJsonString).then(() => {
      this.alertService.success('JSON copied to clipboard');
    }).catch(() => {
      this.alertService.error('Failed to copy to clipboard');
    });
  }

  exportToFile() {
    if (this.selectedForm) {
      const jsonStr = JSON.stringify(this.selectedForm, null, 2);
      const blob = new Blob([jsonStr], {type: 'application/json'});
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${this.selectedForm.name.replace(/\s+/g, '_')}.json`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    }
  }

  // Custom validator to ensure form name is unique
  private uniqueFormNameValidator(): ValidatorFn {
    return (control: AbstractControl) => {
      const formName = control.value;
      if (!formName) return null;

      // Check if the name is already used by another form
      const isDuplicate = this.forms.some(form =>
        form.name === formName &&
        (!this.selectedForm || form.id !== this.selectedForm.id)
      );

      return isDuplicate ? { duplicateName: true } : null;
    };
  }
}
