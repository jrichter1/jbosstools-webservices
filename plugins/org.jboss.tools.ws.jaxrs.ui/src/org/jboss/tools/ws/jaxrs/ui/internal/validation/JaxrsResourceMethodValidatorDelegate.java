/******************************************************************************* 
 * Copyright (c) 2012 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.ws.jaxrs.ui.internal.validation;

import static org.jboss.tools.ws.jaxrs.core.utils.Annotation.VALUE;
import static org.jboss.tools.ws.jaxrs.core.utils.JaxrsClassnames.CONTEXT;
import static org.jboss.tools.ws.jaxrs.core.utils.JaxrsClassnames.COOKIE_PARAM;
import static org.jboss.tools.ws.jaxrs.core.utils.JaxrsClassnames.HEADER_PARAM;
import static org.jboss.tools.ws.jaxrs.core.utils.JaxrsClassnames.MATRIX_PARAM;
import static org.jboss.tools.ws.jaxrs.core.utils.JaxrsClassnames.PATH_PARAM;
import static org.jboss.tools.ws.jaxrs.core.utils.JaxrsClassnames.QUERY_PARAM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsMetamodel;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsNameBinding;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsProvider;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResource;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResourceField;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResourceMethod;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.IJaxrsParamConverterProvider;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.IJaxrsProvider;
import org.jboss.tools.ws.jaxrs.core.utils.Annotation;
import org.jboss.tools.ws.jaxrs.core.utils.JavaMethodParameter;
import org.jboss.tools.ws.jaxrs.core.utils.JdtUtils;
import org.jboss.tools.ws.jaxrs.core.utils.ParameterType;
import org.jboss.tools.ws.jaxrs.ui.internal.utils.Logger;
import org.jboss.tools.ws.jaxrs.ui.preferences.JaxrsPreferences;

/**
 * JAX-RS Resource Method validator.
 * 
 * @author Xavier Coulon
 * 
 */
public class JaxrsResourceMethodValidatorDelegate extends AbstractJaxrsElementValidatorDelegate<JaxrsResourceMethod> {

	/**
	 * The parameter type names that can be annotated with <code>Context</code>.
	 */
	private final static List<String> CONTEXT_TYPE_NAMES = new ArrayList<String>(Arrays.asList(
			"javax.ws.rs.core.HttpHeaders", "javax.ws.rs.core.UriInfo", "javax.ws.rs.core.Request",
			"javax.servlet.http.HttpServletRequest", "javax.servlet.http.HttpServletResponse",
			"javax.servlet.ServletConfig", "javax.servlet.ServletContext", "javax.ws.rs.core.SecurityContext"));

	private static final Pattern alphaNumPattern = Pattern.compile("[a-zA-Z1-9]([a-zA-Z1-9]|\\.|-|_)*");

	private final IMarkerManager markerManager;

	public JaxrsResourceMethodValidatorDelegate(final IMarkerManager markerManager) {
		this.markerManager = markerManager;
	}

	/**
	 * @throws CoreException
	 * @see org.jboss.tools.ws.jaxrs.ui.internal.validation.AbstractJaxrsElementValidatorDelegate#internalValidate(Object)
	 */
	@Override
	void internalValidate(final JaxrsResourceMethod resourceMethod) throws CoreException {
		Logger.debug("Validating element {}", resourceMethod);
		// markers were already removed at the Resource level, they should *not*
		// be removed again here (because another resource method
		// of the same parent resouce may already have been validated and have
		// markers created.
		resourceMethod.resetProblemLevel();
		validatePublicModifierOnJavaMethod(resourceMethod);
		validateNoUnboundPathAnnotationTemplateParameters(resourceMethod);
		validateNoUnboundPathParamAnnotationValues(resourceMethod);
		validateNoUnauthorizedContextAnnotationOnJavaMethodParameters(resourceMethod);
		validateAtMostOneMethodParameterWithoutAnnotation(resourceMethod);
		validateAtLeastOneProviderWithBinding(resourceMethod);
		validateParameterTypes(resourceMethod);
	}

	/**
	 * Validates the type of all parameters annotated with {@code @PathParam},
	 * {@code @QueryParam} and {@code @MatrixParam}.
	 * 
	 * @param resourceMethod
	 *            the resource method to validate
	 * @throws CoreException
	 * @see JaxrsParameterValidatorDelegate
	 */
	private void validateParameterTypes(final JaxrsResourceMethod resourceMethod) throws CoreException {
		// for now, we bypass this validation if the metamodel has at least one ParamConverterProvider
		final List<IJaxrsParamConverterProvider> allParamConverterProviders = resourceMethod.getMetamodel().findAllParamConverterProviders();
		if(allParamConverterProviders != null && ! allParamConverterProviders.isEmpty()) {
			return;
		}
		
		final JaxrsParameterValidatorDelegate parameterValidatorDelegate = new JaxrsParameterValidatorDelegate();
		final List<JavaMethodParameter> methodParameters = resourceMethod.getJavaMethodParameters();
		for (JavaMethodParameter methodParameter : methodParameters) {
			if (!methodParameter.hasAnnotation(PATH_PARAM) && !methodParameter.hasAnnotation(QUERY_PARAM)
					&& !methodParameter.hasAnnotation(MATRIX_PARAM) && !methodParameter.hasAnnotation(COOKIE_PARAM)
					&& !methodParameter.hasAnnotation(HEADER_PARAM)) {
				continue;
			}
			final ParameterType type = methodParameter.getType();
			final boolean isValid = parameterValidatorDelegate.validate(type, resourceMethod.getMetamodel()
					.getJavaProject(), new NullProgressMonitor());
			if (!isValid) {
				markerManager.addMarker(resourceMethod, methodParameter.getType().getNameRange(),
						JaxrsValidationMessages.RESOURCE_METHOD_INVALID_ANNOTATED_PARAMETER_TYPE,
						new String[] { type.getQualifiedName() },
						JaxrsPreferences.RESOURCE_METHOD_INVALID_ANNOTATED_PARAMETER_TYPE);
			}
		}

	}

	/**
	 * Validates that there is at least one {@link JaxrsProvider} annotated with
	 * exactly the same custom {@link JaxrsNameBinding} annotation(s) in the
	 * metamodel.
	 * 
	 * @param resource
	 *            the {@link JaxrsResource} to validate.
	 * @throws CoreException
	 */
	private void validateAtLeastOneProviderWithBinding(final JaxrsResourceMethod resourceMethod) throws CoreException {
		if (resourceMethod == null) {
			return;
		}
		final Map<String, Annotation> nameBindingAnnotations = resourceMethod.getNameBindingAnnotations();
		if (nameBindingAnnotations.isEmpty()) {
			return;
		}
		final JaxrsMetamodel metamodel = resourceMethod.getMetamodel();
		// take the first NameBinding annotation and look for Providers that
		// have this annotation, too
		final String firstNameBindingAnnotationClassName = nameBindingAnnotations.keySet().iterator().next();
		final Set<String> allBindingAnnotationNames = nameBindingAnnotations.keySet();
		final List<IJaxrsProvider> annotatedProviders = metamodel
				.findProvidersByAnnotation(firstNameBindingAnnotationClassName);
		for (IJaxrsProvider provider : annotatedProviders) {
			if (provider.getNameBindingAnnotations().keySet().equals(allBindingAnnotationNames)) {
				// provider is valid, at least one method has all those bindings
				return;
			}
		}
		// otherwise, add a problem marker
		final ISourceRange nameRange = nameBindingAnnotations.get(firstNameBindingAnnotationClassName)
				.getJavaAnnotation().getNameRange();
		markerManager.addMarker(resourceMethod, nameRange, JaxrsValidationMessages.PROVIDER_MISSING_BINDING,
				new String[] { firstNameBindingAnnotationClassName }, JaxrsPreferences.PROVIDER_MISSING_BINDING);
	}

	/**
	 * Validate that at most one method parameter is not annotated with a JAX-RS
	 * annotation. This non-annotated parameter is the "Entity parameter",
	 * coming from the client's request body, unmarshalled by the appropriate
	 * {@link MesssageBodyReader}.
	 * 
	 * @return
	 * @throws CoreException
	 */
	private void validateAtMostOneMethodParameterWithoutAnnotation(final JaxrsResourceMethod resourceMethod)
			throws CoreException {
		int counter = 0;
		for (JavaMethodParameter parameter : resourceMethod.getJavaMethodParameters()) {
			// Should count parameters annotated with:
			// @MatrixParam, @QueryParam, @PathParam, @CookieParam,
			// @HeaderParam, @Context or @FormParam
			final Map<String, Annotation> jaxrsAnnotations = parameter.getAnnotations();
			if (jaxrsAnnotations.size() == 0) {
				counter++;
			}
		}
		if (counter > 1) {
			final ISourceRange nameRange = resourceMethod.getJavaElement().getNameRange();
			markerManager.addMarker(resourceMethod, nameRange,
					JaxrsValidationMessages.RESOURCE_METHOD_MORE_THAN_ONE_UNANNOTATED_PARAMETER, new String[0],
					JaxrsPreferences.RESOURCE_METHOD_MORE_THAN_ONE_UNANNOTATED_PARAMETER);
		}
	}

	/**
	 * Validates that the method parameters annotated with <code>Context</code>
	 * are of the supported types in the spec: <code>UriInfo</code>,
	 * <code>HttpHeaders<code>, <code>ServletConfig</code>,
	 * <code>ServletContext</code>, <code>HttpServletRequest</code> ,
	 * <code>Request</code>, <code>HttpServletResponse</code> and
	 * <code>@link Response</code>.
	 * 
	 * @return
	 * @throws CoreException
	 */
	private void validateNoUnauthorizedContextAnnotationOnJavaMethodParameters(final JaxrsResourceMethod resourceMethod)
			throws CoreException {
		for (JavaMethodParameter parameter : resourceMethod.getJavaMethodParameters()) {
			final Annotation contextAnnotation = parameter.getAnnotation(CONTEXT);
			final String typeName = parameter.getType().getQualifiedName();
			if (contextAnnotation != null && typeName != null && !CONTEXT_TYPE_NAMES.contains(typeName)) {
				final ISourceRange range = contextAnnotation.getJavaAnnotation().getSourceRange();
				markerManager.addMarker(resourceMethod, range,
						JaxrsValidationMessages.RESOURCE_METHOD_ILLEGAL_CONTEXT_ANNOTATION,
						new String[] { CONTEXT_TYPE_NAMES.toString() },
						JaxrsPreferences.RESOURCE_METHOD_ILLEGAL_CONTEXT_ANNOTATION);
			}
		}
	}

	/**
	 * Checks that there is no unbound Path template parameter in the
	 * <code>@Path</code> annotations by checking the method @PathParam
	 * annotated parameters. Report a problem if a Path template parameter has
	 * no equivalent in the java method's parameters.
	 * 
	 * @return errors in case of mismatch, empty list otherwise.
	 * @throws CoreException
	 */
	private void validateNoUnboundPathAnnotationTemplateParameters(final JaxrsResourceMethod resourceMethod)
			throws CoreException {
		final Map<String, Annotation> pathParamValueProposals = resourceMethod.getPathParamValueProposals();
		final List<String> pathParamValues = new ArrayList<String>();
		// retrieve all @PathParam annotation on method arguments and on
		// resource fields
		for (JavaMethodParameter parameter : resourceMethod.getJavaMethodParameters()) {
			final Annotation annotation = parameter.getAnnotation(PATH_PARAM);
			if (annotation != null && annotation.getValue() != null) {
				pathParamValues.add(annotation.getValue());
			}
		}
		final List<JaxrsResourceField> resourceFields = resourceMethod.getParentResource().getAllFields();
		for (JaxrsResourceField resourceField : resourceFields) {
			final Annotation annotation = resourceField.getPathParamAnnotation();
			if (annotation != null && annotation.getValue() != null) {
				pathParamValues.add(annotation.getValue());
			}
		}
		for (Entry<String, Annotation> pathTemplateParameterEntry : pathParamValueProposals.entrySet()) {
			final String pathTemplateParameter = pathTemplateParameterEntry.getKey();
			if (!pathParamValues.contains(pathTemplateParameter)) {
				final Annotation pathTemplateParameterAnnotation = pathTemplateParameterEntry.getValue();
				// look-up source range for annotation value
				final ISourceRange range = resolveAnnotationParamSourceRange(pathTemplateParameterAnnotation,
						pathTemplateParameter);
				markerManager.addMarker(
						resourceMethod,
						range,
						JaxrsValidationMessages.RESOURCE_METHOD_UNBOUND_PATH_ANNOTATION_TEMPLATE_PARAMETER,
						new String[] { pathTemplateParameter,
								JdtUtils.getReadableMethodSignature(resourceMethod.getJavaElement()) },
						JaxrsPreferences.RESOURCE_METHOD_UNBOUND_PATH_ANNOTATION_TEMPLATE_PARAMETER);
			}
		}
	}

	/**
	 * Report a problem for each <code>@PathParam</code> annotation value that
	 * have no counterpart in the <code>@Path</code> template parameters.
	 * 
	 * @return
	 * @throws CoreException
	 */
	private void validateNoUnboundPathParamAnnotationValues(final JaxrsResourceMethod resourceMethod)
			throws CoreException {
		final Map<String, Annotation> pathParamValueProposals = resourceMethod.getPathParamValueProposals();
		for (JavaMethodParameter parameter : resourceMethod.getJavaMethodParameters()) {
			final Annotation pathParamAnnotation = parameter.getAnnotation(PATH_PARAM);
			if (pathParamAnnotation != null) {
				final String pathParamValue = pathParamAnnotation.getValue();
				if (pathParamValue != null) {
					if (!alphaNumPattern.matcher(pathParamValue).matches()) {
						final ISourceRange range = JdtUtils.resolveMemberPairValueRange(
								pathParamAnnotation.getJavaAnnotation(), VALUE);
						markerManager.addMarker(resourceMethod, range,
								JaxrsValidationMessages.RESOURCE_METHOD_INVALID_PATHPARAM_ANNOTATION_VALUE,
								new String[] { pathParamValue },
								JaxrsPreferences.RESOURCE_METHOD_INVALID_PATHPARAM_ANNOTATION_VALUE);
					} else if (!pathParamValueProposals.keySet().contains(pathParamValue)) {
						final ISourceRange range = JdtUtils.resolveMemberPairValueRange(
								pathParamAnnotation.getJavaAnnotation(), VALUE);
						markerManager.addMarker(resourceMethod, range,
								JaxrsValidationMessages.RESOURCE_METHOD_UNBOUND_PATHPARAM_ANNOTATION_VALUE,
								new String[] { pathParamValue },
								JaxrsPreferences.RESOURCE_METHOD_UNBOUND_PATHPARAM_ANNOTATION_VALUE);
					}
				}
			}
		}
	}

	/**
	 * Resolves the location of the Path parameter in the source range, narrowed
	 * to the minimal value. For instance, the returned range corresponds to the
	 * location of the path parameter, including curly brackets and the the
	 * optional regexp in the given <code>@Path</code> annotation
	 * 
	 * @param pathTemplateParameterAnnotation
	 *            the <code>@Path</code> annotation
	 * @param pathTemplateParameter
	 *            the parameter that should be found in the given annotation
	 *            value
	 * @return
	 * @throws JavaModelException
	 */
	private ISourceRange resolveAnnotationParamSourceRange(final Annotation pathTemplateParameterAnnotation,
			final String pathTemplateParameter) throws JavaModelException {
		// refine source range for path parameter in the value (including
		// whitespaces between starting curly bracket and param name)
		final ISourceRange valueRange = JdtUtils.resolveMemberPairValueRange(
				pathTemplateParameterAnnotation.getJavaAnnotation(), VALUE);
		final String annotationValue = pathTemplateParameterAnnotation.getValue();
		final Pattern p = Pattern.compile("\\{\\s*" + Pattern.quote(pathTemplateParameter));
		final Matcher matcher = p.matcher(annotationValue);
		if (matcher.find()) {
			final int start = matcher.start();
			final int end = annotationValue.indexOf('}', start);
			return new SourceRange(valueRange.getOffset() + start + 1, end - start + 1);
		}
		return valueRange;
	}

	/**
	 * As per specification, the java method should have a public modifier.
	 * 
	 * @param resourceMethod
	 * @throws CoreException
	 */
	private void validatePublicModifierOnJavaMethod(final JaxrsResourceMethod resourceMethod) throws CoreException {
		final IMethod javaMethod = resourceMethod.getJavaElement();
		final JaxrsResource parentResource = resourceMethod.getParentResource();
		if (javaMethod == null || parentResource == null || parentResource.getJavaElement() == null) {
			return;
		}
		if (!parentResource.getJavaElement().isInterface() && !Flags.isPublic(javaMethod.getFlags())) {
			final ISourceRange nameRange = javaMethod.getNameRange();
			markerManager.addMarker(resourceMethod, nameRange,
					JaxrsValidationMessages.RESOURCE_METHOD_NO_PUBLIC_MODIFIER,
					new String[] { resourceMethod.getName() }, JaxrsPreferences.RESOURCE_METHOD_NO_PUBLIC_MODIFIER);
		}
	}

}