package eu.qualify.food4me

class StoredLog {
    protected def logger
    protected Map messages = [:]

    public StoredLog(def logger) {
        this.logger = logger
    }

    public def exception = { message, exception -> log( "error", message, exception ) }
    public def error = { message -> log( "error", message, null ) }
    public def warn = { message -> log( "warn", message, null ) }
    public def info = { message -> log( "info", message, null ) }
    public def debug = { message -> log( "debug", message, null ) }
    public def trace = { message -> log( "trace", message, null ) }

    public def log = { level, message, exception ->
        // Log to original
        if( exception )
            logger."$level"(message, exception)
        else
            logger."$level"(message)

        // Store message
        if( !messages[level] )
            messages[level] = []

        messages[level] << message
    }

    public def clear(def level = null) {
        if( level ) {
            messages[level] = []
        } else {
            messages = [:]
        }
    }

    public def get(level = null) {
        level ? messages[level] : messages
    }
}