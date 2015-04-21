changeSet(id: "propertyBoundries", author: "ferry") {
    addColumn(tableName: "property") {
        column(name: "minimum_value", type: "numeric(19, 2)")
        column(name: "maximum_value", type: "numeric(19, 2)")
    }
}