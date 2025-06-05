package net.dungeonhub.application.enums

enum class CntRequestType(valueRange: String, val description: String) {
    UNDER_THREE("<3", "Less than 3m"),
    THREE_TO_FIVE("3-5", "3m-5m"),
    FIVE_TO_TEN("5-10", "5m-10m"),
    TEN_TO_FIVETEEN("10-15", "10m-15m"),
    FIVETEEN_TO_TWENTY("15-20", "15m-20m"),
    TWENTY_TO_TWENTIFIVE("20-25", "20m-25m"),
    TWENTIFIVE_TO_FIFTY("25-50", "25m-50m"),
    FIFTY_TO_HUNDRED("50-100", "50m-100m"),
    HUNDRED_TO_TWOHUNDRED("100-200", "100m-200m"),
    TWOHUNDRED_TO_FOURHUNDRED("200-400", "200m-400m"),
    OVER_FOURHUNDRED("400+", "400m+");

    val buttonId = "cnt_$valueRange"
    val modalId = "${buttonId}_modal"
    val descriptionId = "${valueRange}_desc"
    val valueId = "${valueRange}_val"
    val requirementId = "${valueRange}_req"
}