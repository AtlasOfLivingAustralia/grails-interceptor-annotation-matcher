package au.org.ala.grails;

import grails.artefact.Interceptor;
import grails.core.ArtefactInfo;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.core.GrailsControllerClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Objects;

import static java.util.Arrays.*;

/**
 * Static helper methods for applying matches to Grails Interceptors based on a given annotation and for finding the
 * effective annotation after
 */
public class AnnotationMatcher {

    private static final Logger log = LoggerFactory.getLogger(AnnotationMatcher.class);

    /**
     * Finds all Controllers and Actions that have the given annotation applied to them and calls interceptor.match for
     * the controller / action
     * @param interceptor The interceptor to apply the matches for
     * @param grailsApplication The current grails application
     * @param annotation The annotation to search for
     */
    public static void matchAnnotation(Interceptor interceptor, GrailsApplication grailsApplication, Class<? extends Annotation> annotation) {
        GrailsClass[] controllers = grailsApplication.getArtefacts("Controller");
        for (GrailsClass controller : controllers) {
            final String controllerName = controller.getLogicalPropertyName();
            Class<?> clazz = controller.getClazz();
            Object namespace = staticFieldValue(clazz, GrailsControllerClass.NAMESPACE_PROPERTY);
            Annotation classAnnotation =  clazz.getAnnotation(annotation);
            if (classAnnotation != null) {
                log.debug("Matching namespace: {}, controller: {}, action: * to interceptor: {}", namespace, controllerName, interceptor.getClass().getName());
                LinkedHashMap<String,Object> args = new LinkedHashMap<>();
                args.put("namespace", namespace);
                args.put("controller", controllerName);
                args.put("action", "*");
                interceptor.match(args);
            } else {
                stream(clazz.getMethods())
                        .filter(method -> method.getAnnotation(annotation) != null && Modifier.isPublic(method.getModifiers()))
                        .forEach( method -> {
                            String actionName = method.getName();
                            log.debug("Matching namespace: {}, controller: {}, action: {} to interceptor: {}", namespace, controllerName, actionName, interceptor.getClass().getName());
                            LinkedHashMap<String,Object> args = new LinkedHashMap<>();
                            args.put("namespace", namespace);
                            args.put("controller", controllerName);
                            args.put("action", actionName);
                            interceptor.match(args);
                        });
                stream(clazz.getDeclaredFields())
                        .filter( field -> field.getAnnotation(annotation) != null )
                        .forEach( field -> {
                            String actionName = field.getName();
                            log.debug("Matching namespace: {}, controller: {}, action: {} to interceptor: {}", namespace, controllerName, actionName, interceptor.getClass().getName());
                            LinkedHashMap<String,Object> args = new LinkedHashMap<>();
                            args.put("namespace", namespace);
                            args.put("controller", controllerName);
                            args.put("action", actionName.endsWith("Flow") ? actionName.substring(0, actionName.length() - 4) : actionName);
                            interceptor.match(args);
//                            interceptor.match(namespace: namespace, controller: controllerName, action: actionName.endsWith('Flow') ? actionName.substring(0, actionName.length() - 4) : actionName)
                        });
            }
        }
    }

    private static Object staticFieldValue(Class<?> clazz, String fieldName) {
        return stream(clazz.getDeclaredFields())
                .filter(field -> Objects.equals(fieldName, field.getName()) && Modifier.isStatic(field.getModifiers()))
                .findFirst()
                .map(field -> { try { return field.get(null); } catch (IllegalAccessException e) { return null; } })
                .orElse(null);
    }

    /**
     * Like grailsApplication.getControllerByLogicalName but can also take a namespace into consideration.
     *
     * @param grailsApplication The current grails application
     * @param namespace The controller namespace or null if no namespace
     * @param controllerName The controller name, ie the name for BookController would be 'book'
     * @return The GrailsControllerClass for the namespace and controller
     */
    public static GrailsControllerClass getControllerByNamespaceAndLogicalName(GrailsApplication grailsApplication, String namespace, String controllerName) {
        ArtefactInfo artefacts = grailsApplication.getArtefactInfo("Controller");
        return stream(artefacts.getGrailsClasses())
                .filter( it -> it instanceof GrailsControllerClass )
                .map( it -> (GrailsControllerClass) it)
                .filter( it -> Objects.equals(controllerName, it.getLogicalPropertyName()) && Objects.equals(namespace, it.getNamespace()))
                .findFirst().orElse(null);
    }

    /**
     * Get the applicable annotation results for a given controller name and action.
     * @param grailsApplication The current grails app
     * @param namespace The controller namespace
     * @param controllerName The controller name
     * @param actionName The controller action
     * @param annotation The annotation type to look for
     * @param <T> The annotation type for the main annotation
     * @param <U> The annotation type for the override annotation
     * @return An annotation result
     */
    public static <T extends Annotation, U extends Annotation> AnnotationResult<T, U> getAnnotation(GrailsApplication grailsApplication, String namespace, String controllerName, String actionName, Class<T> annotation) {
        return getAnnotation(grailsApplication, namespace, controllerName, actionName, annotation, null);
    }

    /**
     * Get the applicable annotation results for a given controller name and action.
     * @param grailsApplication The current grails app
     * @param namespace The controller namespace
     * @param controllerName The controller name
     * @param actionName The controller action
     * @param annotation The annotation type to look for
     * @param overrideAnnotationType  The annotation type that can cancel a previous annotation
     * @param <T> The annotation type for the main annotation
     * @param <U> The annotation type for the override annotation
     * @return An annotation result
     */
    public static <T extends Annotation, U extends Annotation> AnnotationResult<T, U> getAnnotation(GrailsApplication grailsApplication, String namespace, String controllerName, String actionName, Class<T> annotation, Class<U> overrideAnnotationType) {
//        def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', controllerName)
        GrailsControllerClass controller = getControllerByNamespaceAndLogicalName(grailsApplication, namespace, controllerName);
        if (controller == null) {
            return new AnnotationResult<>();
        }
        Class<?> cClazz = controller.getClazz();

        String methodName = orDefault(actionName, controller.getDefaultAction());
        // The action annotation may be applied to either a method or a property
        T actionAnnotation;
        U overrideAnnotation;
        // Look for a method on the controller whose name matches the action...
        AccessibleObject action =
                stream(cClazz.getMethods())
                        .filter( method -> Objects.equals(methodName, method.getName()) && Modifier.isPublic(method.getModifiers()))
                        .findFirst()
                        .map( method -> (AccessibleObject) method)
                        .orElseGet(() -> findField(cClazz, methodName));


        if (action != null) {
            actionAnnotation = action.getAnnotation(annotation);
            if (overrideAnnotationType != null) {
                overrideAnnotation = action.getAnnotation(overrideAnnotationType);
            } else {
                overrideAnnotation = null;
            }
        } else {
            actionAnnotation = null;
            overrideAnnotation = null;
        }

        T classAnnotation = cClazz.getAnnotation(annotation);
        return new AnnotationResult<>(classAnnotation, actionAnnotation, overrideAnnotation);
    }

    private static String orDefault(String string, String other) {
        return string == null || string.trim().isEmpty() ? other : string;
    }

    /**
     * Find the field for an action name, looking for Spring Webflow actions as well (ie ${actionName}Flow)
     * @param clazz The class to search
     * @param actionName The grails action name to search for
     * @return The field that matches the action name or null
     */
    private static Field findField(Class<?> clazz, String actionName) {
        // if a method could not be found, look for a property (private field) on the class, for when actions are declared in this style:
        // def action = { ... }
        final String flowActionName = actionName + "Flow";

        return stream(clazz.getDeclaredFields())
                .filter( it -> Objects.equals(actionName, it.getName()) )
                .findFirst()
                .orElseGet( () -> stream(clazz.getDeclaredFields())
                        .filter(it -> Objects.equals(flowActionName, it.getName()))
                        .findFirst()
                        .orElse(null)
                );
    }

    static class AnnotationResult<T extends Annotation, U extends Annotation> {
        public final T controllerAnnotation;
        public final T actionAnnotation;
        public final U overrideAnnotation;

        AnnotationResult() {
            controllerAnnotation = null;
            actionAnnotation = null;
            overrideAnnotation = null;
        }

        AnnotationResult(T controllerAnnotation, T actionAnnotation, U overrideAnnotation) {
            this.controllerAnnotation = controllerAnnotation;
            this.actionAnnotation = actionAnnotation;
            this.overrideAnnotation = overrideAnnotation;
        }

        /**
         * Returns the annotation that would apply for the action, either the action annotation or the controller's
         * annotation if the action didn't have one.  The override annotation is not considered in this method.
         * @return The effective annotation for this result
         */
        T effectiveAnnotation() {
            return actionAnnotation != null ? actionAnnotation : controllerAnnotation;
        }
    }

}
