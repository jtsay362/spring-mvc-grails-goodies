package com.ngweb.web.springmvc.grails

import groovy.util.GroovyScriptEngine

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.lang.StringUtils

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.ui.Model
import org.springframework.web.util.UrlPathHelper
import org.springframework.web.context.request.RequestContextHolder as RCH

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.DefaultGrailsUrlMappingsClass
import org.codehaus.groovy.grails.commons.metaclass.MetaClassEnhancer
import org.codehaus.groovy.grails.plugins.web.api.ControllersApi
import org.codehaus.groovy.grails.web.mapping.*
import org.codehaus.groovy.grails.web.plugins.support.WebMetaUtils
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest

import com.google.common.base.CaseFormat

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GrailsControllerAdapter 
{	
	GrailsControllerAdapter() 
	{
	}
	
	def handleRequest(
		HttpServletRequest request, 
		HttpServletResponse response, 
		Model model,
		ServletContext servletContext, 
		ApplicationContext applicationContext,
		Map options
  ) 
	{		
		def grailsWebRequest = new GrailsWebRequest(request, response,
			servletContext, applicationContext)							
		
		RCH.setRequestAttributes(grailsWebRequest)

		/*
		mLogger.info("new params = " +  RCH.currentRequestAttributes().params)
		mLogger.info("new flash = " + RCH.currentRequestAttributes().flashScope) */
				
		def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
					
		Class urlMappingsClass = null
				
		GroovyScriptEngine groovyScriptEngine = null		
		
		if (mCachedUrlMappingsClass == null)
		{
			groovyScriptEngine = makeGroovyScriptEngine(options)
			
			urlMappingsClass = loadClass(options.urlMappingsClassName, 
				groovyScriptEngine)

			boolean developmentMode = (options.developmentMode ?: false)
			
			if (!developmentMode)
			{				
				mCachedUrlMappingsClass = urlMappingsClass
			}
		} 
		else  
		{
			urlMappingsClass = mCachedUrlMappingsClass
		}
		
		def urlMatches = matchUri(urlMappingsClass, request, applicationContext,
			options)
												
		if (urlMatches.length == 0) {
			mLogger.warn("no url matches for " + uri)
			response.sendError(404)
			return null
		}
		
		def urlMatch = urlMatches[0]
						
		def explicitUri = urlMatch.getURI()
		 
		// Needs test
		if (explicitUri != null) {
			// Need to convert from GString to ordinary Java string
			return "redirect:${explicitUri}".toString() 
		} 
		
		if (urlMatch.viewName != null) 
		{
			return urlMatch.viewName.toString()
		}		
		
		urlMatch.configure(grailsWebRequest)
		
		def controllerName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL,
			urlMatch.controllerName) + "Controller"
				
		if (mLogger.isDebugEnabled())
		{										
			mLogger.debug("using controller " + controllerName)
		}
		
		def packagePrefix = StringUtils.trimToEmpty(options.controllerPackageName) 
			
		if (!packagePrefix.isEmpty())
		{
			packagePrefix += '.'
		}
							
		def controllerClass = loadClass(packagePrefix + controllerName, 
			groovyScriptEngine) 
								
		def controller = controllerClass.newInstance() 					

		enhanceController(controller, applicationContext, model, options)

		def actionName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL,
			urlMatch.actionName ?: controller.defaultAction ?: "index")
	
		if (mLogger.isDebugEnabled())
		{
			mLogger.debug("using action " + actionName)
		}
									
	  def rv = controller."${actionName}"()

		if (mLogger.isDebugEnabled()) 
		{			 		
			mLogger.debug("controller returned " + rv)
		}
		
		controller.modelAndView
	}
		
	/** Create a script engine only if necessary. */
	private GroovyScriptEngine makeGroovyScriptEngine(Map options) 
	{
		if (options.developmentMode) 
		{		
			//def roots = new String[options.sourcePaths.length]
			//roots[0] = "src/main/groovy"									 
			return new GroovyScriptEngine(options.sourcePaths)
		}
		
		return null
	}
	
	private Class loadClass(
		String className, 
		GroovyScriptEngine gse
  )  
	{
		if (gse == null)
		{
			return getClass().classLoader.loadClass(className, true)
		} 

		String pathName = className.replace('.' as char, '/' as char)
		
		gse.loadScriptByName(pathName + ".groovy")						
	} 
	
	private def matchUri(
		Class urlMappingsClass, 
		HttpServletRequest request,
		ApplicationContext applicationContext,
		Map options
  )
	{
		def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
		
		def mappingClass = new DefaultGrailsUrlMappingsClass(urlMappingsClass)
		
		def mappingList = evaluator.evaluateMappings(
			mappingClass.getMappingsClosure())
		
		def mappingsHolder = new DefaultUrlMappingsHolder(mappingList)
						
		def urlPathHelper = new UrlPathHelper()
		
		def uri = StringUtils.removeEnd(StringUtils.removeStart(
			urlPathHelper.getPathWithinApplication(request), options.pathPrefix), 
		  options.pathSuffixToStrip)
		
		mappingsHolder.matchAll(uri)
	}
	
	private def enhanceController(Object controller, 
		ApplicationContext applicationContext, Model model, Map options) 
	{
		WebMetaUtils.registerCommonWebProperties(
			controller.metaClass, applicationContext.getBean(GrailsApplication.class))
					
		controller.metaClass.getModel = {
			model.asMap()
		}

		controller.metaClass.getApplicationContext = {
			applicationContext
		}
		
		controller.metaClass.getPathPrefix = {
			options.pathPrefix
		}
		
		controller.metaClass.getPathSuffixToStrip = {
			options.pathSuffixToStrip
		}
		
		// Add the render(), redirect(), forward(), etc. methods to the controller
		def controllersApi = new ControllersApi()
		def enhancer = new MetaClassEnhancer()
		enhancer.addApi(controllersApi)
		enhancer.enhance(controller.metaClass)
		
		applicationContext.autowireCapableBeanFactory.autowireBeanProperties(
			controller, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
	}	
	
	private Class mCachedUrlMappingsClass = null
		
	private Logger mLogger = LoggerFactory.getLogger(getClass().getName())
}