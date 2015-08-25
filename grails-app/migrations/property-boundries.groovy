databaseChangeLog = {

	changeSet(author: "aboorsma (generated)", id: "1429626136235-1") {
		addColumn(tableName: "property") {
			column(name: "maximum_value", type: "numeric(19, 2)")
		}
	}

	changeSet(author: "aboorsma (generated)", id: "1429626136235-2") {
		addColumn(tableName: "property") {
			column(name: "minimum_value", type: "numeric(19, 2)")
		}
	}
}
