package com.ngweb.web.springmvc.grails

import java.lang.reflect.Modifier

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
	GrailsControllerAdapter(Map options) 
	{
		mPathPrefix = options.pathPrefix
		mPathSuffixToStrip = options.pathSuffixToStrip
		mUrlCaseFormat = options.urlCaseFormat
		
		mDevelopmentMode = (options.developmentMode ?: false)
		mSourcePaths = options.sourcePaths
		
		mUrlMappingsClassName = options.urlMappingsClassName
		
		mPackagePrefix = StringUtils.trimToEmpty(options.controllerPackageName)
		
		if (!mPackagePrefix.isEmpty())
		{
			mPackagePrefix += '.'
		}				
	}
	
	def handleRequest(
		HttpServletRequest request, 
		HttpServletResponse response, 
		Model model,
		ServletContext servletContext, 
		ApplicationContext applicationContext
  ) 
	{		
		def grailsWebRequest = new GrailsWebRequest(request, response,
			servletContext, applicationContext)							
		
		RCH.setRequestAttributes(grailsWebRequest)

		/*
		mLogger.info("new params = " +  RCH.currentRequestAttributes().params)
		mLogger.info("new flash = " + RCH.currentRequestAttributes().flashScope) */
				
		Class urlMappingsClass = null
				
		GroovyScriptEngine groovyScriptEngine = null		
		
		if (mCachedUrlMappingsClass == null)
		{
			groovyScriptEngine = makeGroovyScriptEngine()
			
			urlMappingsClass = loadClass(mUrlMappingsClassName, 
				groovyScriptEngine)

			if (!mDevelopmentMode)
			{				
				mCachedUrlMappingsClass = urlMappingsClass
			}
		} 
		else  
		{
			urlMappingsClass = mCachedUrlMappingsClass
		}
		
		def uriToMatch = computeUriToMatch(request) 		

		def urlMatches = matchUri(uriToMatch, urlMappingsClass, applicationContext)
													
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
		
		String controllerName = mUrlCaseFormat.to(CaseFormat.UPPER_CAMEL,
			urlMatch.controllerName) + "Controller"
				
		if (mLogger.isDebugEnabled())
		{										
			mLogger.debug("using controller " + controllerName)
		}
		
		def controllerClass = loadClass(mPackagePrefix + controllerName, 
			groovyScriptEngine) 
								
		def controller = controllerClass.newInstance() 					

		enhanceController(controller, applicationContext, model)

		def actionName = computeActionName(urlMatch, controller, controllerName) 
	
		if (mLogger.isDebugEnabled())
		{
			mLogger.debug("using action " + actionName)
		}
									
		if (actionName == null)
		{
			if (mLogger.isDebugEnabled())
			{
				mLogger.debug("no action found, returning default view " + uriToMatch)
			}

			// No action defined, just use default view			
			String rv = mPathPrefix + uriToMatch
			
			if (rv.endsWith("/")) 
			{
				rv += "index"
			}			
			
			rv
		} 
		else 
		{
		  def rv = controller."${actionName}"()
	
			if (mLogger.isDebugEnabled()) 
			{			 		
				mLogger.debug("controller returned " + rv)
			}
			
			return rv
		}
	}
		
	/** Create a script engine only if necessary. */
	private GroovyScriptEngine makeGroovyScriptEngine() 
	{
		if (mDevelopmentMode) 
		{		
			return new GroovyScriptEngine(mSourcePaths)
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
	
	private String computeUriToMatch( 
		HttpServletRequest request
  )
	{		
		def urlPathHelper = new UrlPathHelper()
		
		StringUtils.removeEnd(StringUtils.removeStart(
			urlPathHelper.getPathWithinApplication(request), mPathPrefix), 
		  mPathSuffixToStrip)
	}
	
	private def matchUri(
		String uriToMatch,
		Class urlMappingsClass,
		ApplicationContext applicationContext
	) {
		def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
		
		def mappingClass = new DefaultGrailsUrlMappingsClass(urlMappingsClass)
		
		def mappingList = evaluator.evaluateMappings(
			mappingClass.mappingsClosure)
		
		def mappingsHolder = new DefaultUrlMappingsHolder(mappingList)
						
		return mappingsHolder.matchAll(uriToMatch)		
	}	
		
	private String computeActionName(
		UrlMappingInfo urlMatch, 
		Object controller,
		String controllerName) {
		
		String actionName = urlMatch.actionName
		
		if (actionName == null) 
		{
			if (controller.metaClass.getMetaProperty("defaultAction"))
			{
				actionName = controller.defaultAction
			}
		}
						
		if (actionName == null) 
		{
			
			/* We get methods from Object like wait() that match. So the 
			number of methods is always > 1 and this is not a good criteria.
			
			boolean foundIndex = false
			int numMethods = 0
			String lastMethodName = null
						
			def methods = controller.metaClass.methods.each {
				method ->
				if (Modifier.isPublic(method.modifiers) &&
						!Modifier.isAbstract(method.modifiers) &&
				    (method.parameterTypes.length == 0)) 
				{
					if (mLogger.isTraceEnabled()) 
					{
						mLogger.trace("Matching method " + method.name)
					}
					
					String name = method.name
					
					if (name == "index") {
						foundIndex = true
					}
					numMethods++						
					lastMethodName = name				
				} else {
					if (mLogger.isTraceEnabled())
					{
						mLogger.debug("Non matching method " + method.name)
					}
				}																
			}
			
			if (foundIndex) {
				actionName = "index"
			} 
			else if (numMethods == 1) {
				actionName = lastMethodName
			} */
			
			if (controller.metaClass.getMetaMethod("index")) 
			{
				actionName = "index"
			}			
		}
		
		if (actionName == null) {
			return null
		}						
			
		mUrlCaseFormat.to(CaseFormat.LOWER_CAMEL, actionName)
	}
		
	private def enhanceController(Object controller, 
		ApplicationContext applicationContext, Model model) 
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
			mPathPrefix
		}
		
		controller.metaClass.getPathSuffixToStrip = {
			mPathSuffixToStrip
		}
		
		// Add the render(), redirect(), forward(), etc. methods to the controller
		def controllersApi = new ControllersApi()
		def enhancer = new MetaClassEnhancer()
		enhancer.addApi(controllersApi)
		enhancer.enhance(controller.metaClass)
		
		applicationContext.autowireCapableBeanFactory.autowireBeanProperties(
			controller, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
	}	
	
	private String mPathPrefix = null
	private String mPathSuffixToStrip = null
	private CaseFormat mUrlCaseFormat = null
	private boolean mDevelopmentMode = false
	private String[] mSourcePaths = null 
	private String mUrlMappingsClassName = null
	private String mPackagePrefix = null
	private Class mCachedUrlMappingsClass = null
			
	private Logger mLogger = LoggerFactory.getLogger(getClass().getName())
}