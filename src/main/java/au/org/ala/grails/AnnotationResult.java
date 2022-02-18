package au.org.ala.grails;

import java.lang.annotation.Annotation;

public class AnnotationResult<T extends Annotation, U extends Annotation> {
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
     *
     * @return The effective annotation for this result
     */
    public T effectiveAnnotation() {
        return actionAnnotation != null ? actionAnnotation : controllerAnnotation;
    }
}
