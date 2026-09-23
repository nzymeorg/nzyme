package app.nzyme.core.database.generic;

public enum ThreeColumnWithKeyHistogramOrderColumn {

    KEY("key"),
    VALUE2("value2"),
    VALUE1("value1");

    private final String columnName;

    ThreeColumnWithKeyHistogramOrderColumn(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() {
        return columnName;
    }

}
