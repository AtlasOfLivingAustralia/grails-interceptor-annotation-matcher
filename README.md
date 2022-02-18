# Grails Annotation Matcher for Interceptors

This is a simple library that can apply a `match` for an annotated Grails controller and/or action.

## Installation

In `build.gradle`:
```groovy
dependencies {
  implementation 'au.org.ala.grails:interceptor-annotation-matcher:1.0.0-SNAPSHOT'
}
```

## Usage
To use, simply call `AnnotationMatcher.matchAnnotation` in your interceptor constructor.  Then, if required, retrieve
the annotation using `AnnotationMatcher.getAnnotation`.  For example, assume a controller or action can be annotation 
an  `@Example` annotation, you would do the following:
```groovy
class SomeInterceptor {
    @PostConstruct
    def init() {
        AnnotationMatcher.matchAnnotation(this, grailsApplcation, Example)
    }
    def before = {
        def example = AnnotationMatcher.getAnnotation(grailsApplcation, namespace, controller, action, Example).effectiveAnnotation()
        // get values from the @Example, etc
        true
    }
    //...
}
```
