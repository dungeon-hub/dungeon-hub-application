package net.dungeonhub.application.exceptions

//TODO remove once carry difficulty is fully implemented
class InvalidSubCommandException : CommandExecutionException("Unknown or missing sub-command")