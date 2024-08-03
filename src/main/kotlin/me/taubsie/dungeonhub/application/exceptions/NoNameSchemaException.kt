package me.taubsie.dungeonhub.application.exceptions

class NoNameSchemaException :
    CommandExecutionException("No role with name-schema to apply found.\nPlease tell the administrators to set them up correctly.")