<html>
	<head>
		<meta name="layout" content="main" />
		<title>Food4me advices</title>
	</head>
	<body>
		<h1 style="font-size: 180%; font-weight: 400">Persoonlijk dieetadvies</h1>
		<g:form action="advices" params="${[language: language]}" name="generate_advices" method="get">
			<fieldset>
				<legend>Nutrients</legend>

				<ul>
					<g:each in="${nutrients}" var="property">
						<li>
							<label for="property_${property.id}">
								${property.entity}
								<g:if test="${property.unit}">
									(${property.unit.code})
								</g:if>
							</label>
							<input id="property_${property.id}" type="text" name="nutrient.${property.entity}.total" />
						</li>
					</g:each>
				</ul>
			</fieldset>
		
			<g:each in="${properties}" var="propertygroup">
				<fieldset>
					<legend>${propertygroup.key}</legend>
					<ul>
						<g:each in="${propertygroup.value}" var="property">
							<li>
								<label for="property_${property.id}">
									${property.entity}
									<g:if test="${property.unit}">
										(${property.unit.code})
									</g:if>
								</label>
								<input id="property_${property.id}" type="text" name="${conversionMap[propertygroup.key]}.${property.entity}" />
							</li>
						</g:each>
					</ul>
				</fieldset>
			</g:each>
			
			<g:submitButton name="Retrieve status" onClick="\$(this).closest('form').attr( 'action', '${g.createLink(action: 'status')}' );" />
			<g:submitButton name="Retrieve advice" onClick="\$(this).closest('form').attr( 'action', '${g.createLink(mapping: 'translatedAdvices', action: 'advices', params: [language: language])}' );"  />
		</g:form>
	</body>
</html>
