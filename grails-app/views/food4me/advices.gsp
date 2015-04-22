<%@ page import="eu.qualify.food4me.ModifiedProperty" %>
<html>
	<head>
		<meta name="layout" content="main" />
		<title>Food4me advices</title>
	</head>
	<body>
		<h1>Food4me advices (${advices.size()})</h1>
		
		<div id="advices">
			<ul>
				<g:each in="${advices}" var="advice">
					<li>
						<strong>${advice.code}</strong>: 
							<g:lines string="${translations[advice.code]}" />
					</li>
				</g:each>
			</ul>
		</div>
		
		<h1>Measurements</h1>
		<div class="measurements">
			<ul>
				<g:set var="i" value="0"/>
				<g:set var="filteredMeasurements" value="${measurements.all.findAll() { !(it.property instanceof eu.qualify.food4me.ModifiedProperty) }}"/>
				<g:each in="${filteredMeasurements.collectAll() { it.property?.propertyGroup }.unique()}" var="propertyGroup">
					<h1>${propertyGroup}</h1>
					<g:each in="${filteredMeasurements.findAll() { it.property.propertyGroup.equals(propertyGroup) } }" var="measurement">
						<g:set var="i" value="${i+1}"/>
						<li>
							<span class='property'>${measurement.property}</span>
							<span class='value'>${measurement.value}</span>
							<g:if test="${references[measurement.property]*.conditions && !references[measurement.property]*.conditions*.conditionType.flatten().contains(eu.qualify.food4me.reference.ReferenceCondition.TYPE_TEXT)}">
								<g:if test="${references[measurement.property] && references[measurement.property].size() > 0}">
									<canvas id="referenceBar${i}" width="300" height="20">
										<p class="canvas-no-support">Your Browser Does Not Support HTML5 canvas!</p>
									</canvas>

									<g:if test="${!measurement.property.minimumValue && !measurement.property.maximumValue}">
										<%
											def referenceSize = references[measurement.property].size()

											def combined = references[measurement.property]*.conditions*.low.flatten() + references[measurement.property]*.conditions*.high.flatten()
											def barMin = combined.min()
											def barMax = combined.max()
											def barRange = (barMax - barMin)
											def value = measurement.value.value
											def valuePointer

											if(value < barMin) {
												valuePointer = 0
											}
											else if(value > barMax) {
												valuePointer = 295
											}
											else {
												valuePointer = ((measurement.value.value - barMin)/barRange)*300 as java.lang.Integer
											}
										%>

										<g:javascript>
											<%
												def prevColor
											%>

											var grid_canvas = document.getElementById("referenceBar${i}");
										var grid = grid_canvas.getContext("2d");

										var my_gradient=grid.createLinearGradient(0,0,300,0);

											<g:each in="${references[measurement.property]}" var="reference" status="x">
												<%
													def color
													switch (reference.color.value) {
														case 10:
															color = 'green'
															break
														case 20:
															color = 'orange'
															break
														case 30:
															color = 'red'
															break
													}
												%>

												my_gradient.addColorStop(${(x*(1/referenceSize))},"${color}");
												<g:if test="${x > 0}">
													my_gradient.addColorStop(${(x*(1/referenceSize))-((1/referenceSize)/4)},"${prevColor}");
												</g:if>

												<%
													prevColor = color
												%>
											</g:each>
											grid.fillStyle = my_gradient;
                                            grid.fillRect(0,0,300,20);
                                            grid.textAlign = "center";
                                            grid.fillStyle = "black";
                                            grid.fillRect(${valuePointer},0,5,20);
                                        grid.font = "11px Arial";
										</g:javascript>
									</g:if>
									<g:else>
										<%
											def barMin = measurement.property.minimumValue
											def barMax = measurement.property.maximumValue
											def barRange = (barMax - barMin)
											def value = measurement.value.value

											if(value < barMin) {
												valuePointer = 0
											}
											else if(value > barMax) {
												valuePointer = 295
											}
											else {
												valuePointer = ((measurement.value.value - barMin)/barRange)*300 as java.lang.Integer
											}
										%>

										<g:javascript>
											<%
												def barRegionStart = 0
											%>

											var grid_canvas = document.getElementById("referenceBar${i}");
										var grid = grid_canvas.getContext("2d");

											<g:each in="${references[measurement.property]}" var="reference" status="x">
												<%
													def color
													switch (reference.color.value) {
														case 10:
															color = 'green'
															break
														case 20:
															color = 'orange'
															break
														case 30:
															color = 'red'
															break
													}
												%>

												<%
													def barRegionMin = reference.subjectCondition.low
													if (!barRegionMin) {
														barRegionMin = barMin
													}

													def barRegionMax = reference.subjectCondition.high
													if (!barRegionMax) {
														barRegionMax = barMax
													}

													def barRegionRange = 300*((barRegionMax-barRegionMin)/barRange)
												%>

												grid.fillStyle = "${color}";
											grid.fillRect(${barRegionStart},0,${barRegionRange},20);

												<%
													barRegionStart = barRegionStart + barRegionRange
												%>
											</g:each>
											grid.fillStyle = "black";
                                            grid.fillRect(${valuePointer},0,5,20);
										</g:javascript>
									</g:else>
								</g:if>
							</g:if>
							<g:else>
								<% def propertyStatus = status.getStatus(measurement.property) %>
								<g:if test="${propertyStatus}">
									<span class="status color_${propertyStatus.color}">${propertyStatus.status}</span>
								</g:if>
							</g:else>
						</li>
					</g:each>
				</g:each>
			</ul>
		</div>
	</body>
</html>
