package com.vaadin.spring.aot;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.router.*;
import com.vaadin.flow.router.internal.DefaultErrorHandler;
import org.apache.catalina.core.ApplicationContextFacade;
import org.atmosphere.cache.UUIDBroadcasterCache;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.config.managed.ManagedServiceInterceptor;
import org.atmosphere.config.service.AtmosphereHandlerService;
import org.atmosphere.container.JSR356AsyncSupport;
import org.atmosphere.cpr.*;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.AbstractBroadcasterProxy;
import org.atmosphere.util.ExcludeSessionBroadcaster;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.util.VoidAnnotationProcessor;
import org.atmosphere.websocket.protocol.SimpleHttpProtocol;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;

import java.util.*;

@AutoConfiguration
@ImportRuntimeHints({ FlowHints.class, FlowComponentsHints.class, AtmosphereHintsRegistrar.class })
class AotAutoConfiguration {

	@Bean
	static FlowBeanDefinitionAotProcessor flowBeanDefinitionRegistryPostProcessor() {
		return new FlowBeanDefinitionAotProcessor();
	}

}

class FlowComponentsHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var c : new Class[] { ToStringSerializer.class, MessageListItem.class })
			hints.reflection().registerType(c, mcs);
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
				DefaultAtmosphereResourceSessionFactory.class, JSR356AsyncSupport.class, DefaultMetaBroadcaster.class,
				AtmosphereHandlerService.class, AbstractBroadcasterProxy.class, AsyncSupportListener.class,
				AtmosphereFrameworkListener.class, ExcludeSessionBroadcaster.class,
				AtmosphereResourceEventListener.class, AtmosphereInterceptor.class, BroadcastFilter.class,
				AtmosphereResource.class, AtmosphereResourceImpl.class, ManagedServiceInterceptor.class));
		all.addAll(AtmosphereFramework.DEFAULT_ATMOSPHERE_INTERCEPTORS);
		return all;
	}

}

/**
 * programmatically registers beans for all types annotated with {@link Route}
 */
class FlowBeanDefinitionAotProcessor
		implements BeanFactoryInitializationAotProcessor, BeanDefinitionRegistryPostProcessor {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (generationContext, beanFactoryInitializationCode) -> {
			var hints = generationContext.getRuntimeHints();
			var reflection = hints.reflection();
			var resources = hints.resources();
			var memberCategories = MemberCategory.values();
			for (var pkg : getVaadinFlowPackages(beanFactory)) {
				var reflections = new Reflections(pkg);
				for (var c : reflections.getSubTypesOf(AppShellConfigurator.class)) {
					reflection.registerType(c, memberCategories);
					resources.registerType(c);
				}
				for (var c : getRouteTypesFor(reflections, pkg)) {
					reflection.registerType(c, memberCategories);
					resources.registerType(c);
				}
				for (var c : reflections.getSubTypesOf(RouterLayout.class)) {
					reflection.registerType(c, memberCategories);
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

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		//
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		var statusBeanName = "default" + FlowAotRoutesStatus.class.getSimpleName();
		logger.debug("postProcessBeanDefinitionRegistry");

		if (registry.containsBeanDefinition(statusBeanName))
			return;

		if (registry instanceof BeanFactory bf) {
			logger.debug(registry.getClass().getName() + " is an instanceof " + bf.getClass().getName());
			for (var pkg : getVaadinFlowPackages(bf)) {
				logger.debug("looking in package [] for any @" + Route.class.getName() + " or @"
						+ RouteAlias.class.getName() + " annotated beans");
				var reflections = new Reflections(pkg);
				for (var c : getRouteTypesFor(reflections, pkg)) {
					logger.debug("registering a bean for the @Route-annotated class [" + c.getName() + "]");
					var bd = BeanDefinitionBuilder.rootBeanDefinition(c).setScope("prototype").getBeanDefinition();
					registry.registerBeanDefinition(c.getName(), bd);
					logger.debug("registering bean [" + bd.getBeanClassName() + "]");
				}
			}
			registry.registerBeanDefinition(statusBeanName,
					BeanDefinitionBuilder.rootBeanDefinition(FlowAotRoutesStatus.class).getBeanDefinition());

		}
		else {
			logger.warn("The " + BeanFactory.class.getName() + " is not an instance of "
					+ BeanDefinitionRegistry.class.getName() + '.'
					+ " Unable to register bean definitions for classes annotated with " + Route.class.getName()
					+ " and " + RouteAlias.class.getName());

		}
	}

	private static Collection<Class<?>> getRouteTypesFor(Reflections reflections, String packageName) {
		var routeyTypes = new HashSet<Class<?>>();
		routeyTypes.addAll(reflections.getTypesAnnotatedWith(Route.class));
		routeyTypes.addAll(reflections.getTypesAnnotatedWith(RouteAlias.class));
		return routeyTypes;
	}

	static class FlowAotRoutesStatus {

	}

	private static List<String> getVaadinFlowPackages(BeanFactory beanFactory) {
		var listOf = new ArrayList<String>();
		listOf.add("com.vaadin");
		listOf.addAll(AutoConfigurationPackages.get(beanFactory));
		return listOf;
	}

}
