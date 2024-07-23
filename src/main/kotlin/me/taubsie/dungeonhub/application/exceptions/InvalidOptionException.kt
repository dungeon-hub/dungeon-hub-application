package me.taubsie.dungeonhub.application.exceptions

class InvalidOptionException @JvmOverloads constructor(name: String, additionalMessage: String? = null) :
    CommandExecutionException(
        getMessage(name, additionalMessage)
    ) {
    companion object {
        private fun getMessage(name: String, additionalMessage: String?): String {
            return if (additionalMessage == null) {
                String.format("The option \"%s\" you entered is invalid.", name)
            } else {
                String.format("The option \"%s\" you entered is invalid:%n%s", name, additionalMessage)
            }
        }
    }
}