package com.vaadin.spring.aot;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.internal.DefaultErrorHandler;
import org.apache.catalina.core.ApplicationContextFacade;
import org.atmosphere.cache.UUIDBroadcasterCache;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.container.JSR356AsyncSupport;
import org.atmosphere.cpr.*;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.util.VoidAnnotationProcessor;
import org.atmosphere.websocket.protocol.SimpleHttpProtocol;
import org.reflections.Reflections;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.util.*;

@AutoConfiguration
@ImportRuntimeHints({ FlowHints.class, AtmosphereHintsRegistrar.class })
class AotAutoConfiguration {

	@Bean
	static FlowBeanFactoryInitializationAotProcessor flowBeanFactoryInitializationAotProcessor() {
		return new FlowBeanFactoryInitializationAotProcessor();
	}

	@Bean
	static MyBDRPP brdpp() {
		return new MyBDRPP();
	}

}

class FlowHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		for (var c : new String[] { NotFoundException.class.getName(),
				"org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler$SupplierCsrfToken",
				ApplicationContextFacade.class.getName(), LoginI18n.class.getName(), LoginI18n.Form.class.getName(),
				LoginI18n.ErrorMessage.class.getName() }) {
			hints.reflection().registerType(TypeReference.of(c), MemberCategory.values());
		}
		hints.resources().registerResource(new ClassPathResource("com/vaadin/flow/component/login/i18n.json"));
	}

}

/* todo upstream these to the Atmosphere project */
class AtmosphereHintsRegistrar implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerResource(new ClassPathResource("org/atmosphere/util/version.properties"));
		var reflectionHints = hints.reflection();

		for (Class<?> c : getAtmosphereClasses()) {
			reflectionHints.registerType(c, MemberCategory.values());
		}
	}

	private Collection<? extends Class<?>> getAtmosphereClasses() {
		var all = new HashSet<>(Set.of(DefaultAtmosphereResourceFactory.class, SimpleHttpProtocol.class,
				AtmosphereResourceLifecycleInterceptor.class, TrackMessageSizeInterceptor.class,
				SuspendTrackerInterceptor.class, DefaultBroadcasterFactory.class, SimpleBroadcaster.class,
				DefaultBroadcaster.class, UUIDBroadcasterCache.class, VoidAnnotationProcessor.class,
				DefaultAtmosphereResourceSessionFactory.class, JSR356AsyncSupport.class, DefaultMetaBroadcaster.class));
		all.addAll(AtmosphereFramework.DEFAULT_ATMOSPHERE_INTERCEPTORS);
		return all;
	}

}

class MyBDRPP implements BeanDefinitionRegistryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		//
	}

	private static List<String> getPackages(BeanFactory beanFactory) {
		var listOf = new ArrayList<String>();
		listOf.add("com.vaadin");
		listOf.addAll(AutoConfigurationPackages.get(beanFactory));
		return listOf;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("postProcessBeanDefinitionRegistry");
		if (registry instanceof BeanFactory bf) {
			System.out.println("Registry is a bean factory");
			for (var pkg : getPackages(bf)) {
				System.out.println(pkg);
				var reflections = new Reflections(pkg);
				var routeyTypes = new HashSet<Class<?>>();
				routeyTypes.addAll(reflections.getTypesAnnotatedWith(Route.class));
				routeyTypes.addAll(reflections.getTypesAnnotatedWith(RouteAlias.class));
				for (var c : routeyTypes) {
					System.out.println(c);
					var bd = BeanDefinitionBuilder.rootBeanDefinition(c).setScope("prototype").getBeanDefinition();
					registry.registerBeanDefinition(c.getName(), bd);
				}
			}
		}
	}

}

class FlowBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (generationContext, beanFactoryInitializationCode) -> {
			var hints = generationContext.getRuntimeHints();
			var reflection = hints.reflection();
			var resources = hints.resources();
			var memberCategories = MemberCategory.values();
			for (var pkg : getPackages(beanFactory)) {
				var reflections = new Reflections(pkg);
				var routeyTypes = new HashSet<Class<?>>();
				routeyTypes.addAll(reflections.getTypesAnnotatedWith(Route.class));
				routeyTypes.addAll(reflections.getTypesAnnotatedWith(RouteAlias.class));
				for (var c : routeyTypes) {
					reflection.registerType(c, memberCategories);
					resources.registerType(c);
				}
				for (var c : reflections.getSubTypesOf(Component.class))
					reflection.registerType(c, memberCategories);
				for (var c : reflections.getSubTypesOf(HasErrorParameter.class))
					reflection.registerType(c, memberCategories);
				for (var c : reflections.getSubTypesOf(ComponentEvent.class))
					reflection.registerType(c, memberCategories);
				for (var c : Set.of("com.vaadin.flow.router.RouteNotFoundError.LazyInit.class",
						DefaultErrorHandler.class.getName()))
					reflection.registerType(TypeReference.of(c), memberCategories);
				for (String r : Set.of("*RouteNotFoundError_dev.html", "*RouteNotFoundError_prod.html"))
					resources.registerPattern(r);
			}
		};
	}

	private static List<String> getPackages(BeanFactory beanFactory) {
		var listOf = new ArrayList<String>();
		listOf.add("com.vaadin");
		listOf.addAll(AutoConfigurationPackages.get(beanFactory));
		return listOf;
	}

}