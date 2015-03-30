class UrlMappings {

	static mappings = {
		"/$language/advices(.$format)?"( controller: "food4me", action: "advices" )
		"/advices(.$format)?"( controller: "food4me", action: "advices" )
		"/status(.$format)?"( controller: "food4me", action: "status" )
		"/references(.$format)?"( controller: "food4me", action: "references" )
		
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller:"food4me", action: "form")
        "500"(view:'/error')
	}
}
