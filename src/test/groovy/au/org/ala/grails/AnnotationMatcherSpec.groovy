package au.org.ala.grails


import grails.artefact.Interceptor
import grails.web.Controller
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class AnnotationMatcherSpec extends Specification implements GrailsUnitTest {

    def "matches annotations on actions"() {
        given:
        def interceptor = Mock(Interceptor)

        grailsApplication.addArtefact('Controller', MockController)

        when:
        AnnotationMatcher.matchAnnotation(interceptor, grailsApplication, MockAnnotation)

        then:
        1 * interceptor.match(namespace: null, controller: 'mock', action: 'action')
    }

    def "matches annotations on classes"() {
        given:
        def interceptor = Mock(Interceptor)

        grailsApplication.addArtefact('Controller', Mock2Controller)

        when:
        AnnotationMatcher.matchAnnotation(interceptor, grailsApplication, MockAnnotation)

        then:
        1 * interceptor.match(namespace: 'v2', controller: 'mock2', action: '*')
    }

    def "matches annotations on web flow actions"() {
        given:
        def interceptor = Mock(Interceptor)

        grailsApplication.addArtefact('Controller', Mock3Controller)

        when:
        AnnotationMatcher.matchAnnotation(interceptor, grailsApplication, MockAnnotation)

        then:
        1 * interceptor.match(namespace: null, controller: 'mock3', action: 'action')
    }

    def "only matches controller if both class and method/field are annotated"() {
        given:
        def interceptor = Mock(Interceptor)

        grailsApplication.addArtefact('Controller', Mock4Controller)

        when:
        AnnotationMatcher.matchAnnotation(interceptor, grailsApplication, MockAnnotation)

        then:
        1 * interceptor.match(namespace: null, controller: 'mock4', action: '*')
    }

    def "test getAnnotation"() {
        given:
        grailsApplication.addArtefact('Controller', MockController)
        grailsApplication.addArtefact('Controller', Mock2Controller)
        grailsApplication.addArtefact('Controller', Mock3Controller)
        grailsApplication.addArtefact('Controller', Mock4Controller)

        when:
        def effective = AnnotationMatcher.getAnnotation(grailsApplication, null, 'mock', 'action', MockAnnotation)

        then:
        effective.effectiveAnnotation() != null
        effective.controllerAnnotation == null
        effective.actionAnnotation != null

        when:
        effective = AnnotationMatcher.getAnnotation(grailsApplication, 'v2', 'mock2', 'action', MockAnnotation)

        then:
        effective.effectiveAnnotation() != null
        effective.controllerAnnotation != null
        effective.actionAnnotation == null

        when:
        effective = AnnotationMatcher.getAnnotation(grailsApplication, null, 'mock3', 'action', MockAnnotation)

        then:
        effective.effectiveAnnotation() != null
        effective.controllerAnnotation == null
        effective.actionAnnotation != null

        when:
        effective = AnnotationMatcher.getAnnotation(grailsApplication, null, 'mock4', 'action1', MockAnnotation, OverrideAnnotation)

        then:
        effective.overrideAnnotation == null
        effective.effectiveAnnotation() != null
        effective.effectiveAnnotation().value() == 'action'
        effective.controllerAnnotation != null
        effective.controllerAnnotation.value() == 'controller'
        effective.actionAnnotation != null
        effective.actionAnnotation.value() == 'action'

    }

}

@Retention(RetentionPolicy.RUNTIME)
@Target([ ElementType.METHOD, ElementType.TYPE, ElementType.FIELD])
@interface MockAnnotation {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ ElementType.METHOD, ElementType.TYPE, ElementType.FIELD])
@interface OverrideAnnotation {
    String value() default "";
}

@Controller
class MockController {
    @MockAnnotation
    def action() {
    }
}

@MockAnnotation
@Controller
class Mock2Controller {
    public static namespace = 'v2'
}

@Controller
class Mock3Controller {

    @MockAnnotation
    def actionFlow = {}
}

@MockAnnotation('controller')
@Controller
class Mock4Controller {

    @MockAnnotation('action')
    def action1() {}

    @MockAnnotation('action2')
    def action2 = {}

    @OverrideAnnotation
    def action3() {}

    @MockAnnotation('flow')
    def action2Flow = {}
}