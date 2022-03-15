package ch.ddis.speakeasy.assignment

interface ChatAssignmentGenerator {

    /**
     * Generates a list of assignments to be used in an evaluation round
     */
    fun generateAssignments() : List<ChatAssignment>

}