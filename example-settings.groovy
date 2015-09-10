dataSource {
	url = "jdbc:postgresql://localhost:5432/food4me"
	driverClassName = "org.postgresql.Driver"
	dialect = org.hibernate.dialect.PostgreSQLDialect
	username = "username"
	password = "password"

	// For production environments pooled should 
	// be set to true, due to other default settings in conf/DataSource.groovy
	// You could either change the next line to true or alter
	// the default configuration in DataSource.groovy (if you know what you are doing)
	pooled = false
}

log4j.external = {
	println "Extending log4j configuration"
	// Extra log4j configuration, if required
}

food4me {
	// Directory to import the data from
	importDirectory = "/tmp/input-food4me"

    // Directory to upload and unzip files to. Should be writable by the tomcat user
    // Defaults to /tmp
    tmpDirectory = "/tmp"
	
	// Administrator password when first starting the application
	// See Bootstrap.groovy
	adminPassword = 'secret'

    // Flag to determine whether to show logs on the advice page
    // This is only recommended when skilled users are viewing the advice pages
    showLogsForAdvices = true
}
