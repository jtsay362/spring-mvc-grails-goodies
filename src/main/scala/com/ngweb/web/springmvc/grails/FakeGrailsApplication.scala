package com.ngweb.web.springmvc.grails

import java.lang.{Boolean => JBoolean}
import java.lang.Class
import java.util.{Map => JMap}

import scala.collection.JavaConversions._

import grails.util.Environment
import grails.util.Metadata
import groovy.util.ConfigObject

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.io.Resource

import org.codehaus.groovy.grails.commons._

class FakeGrailsApplication extends GrailsApplication 
{
  import FakeGrailsApplication._
  
  override def getConfig() : ConfigObject = null

  override def getFlatConfig() : JMap[String, Object] = FLAT_CONFIG

  override def getClassForName(name : String) : Class[_] =
    Class.forName(name)
      
  override def getClassLoader() : ClassLoader = 
    this.getClass.getClassLoader
  
  override def getAllClasses() : Array[Class[_]] = Array()
  
  override def getAllArtefacts() : Array[Class[_]] = Array()
  
  override def getMainContext() : ApplicationContext = mApplicationContext
  
  override def
  setMainContext
  (
    context : ApplicationContext
  ) : Unit = mApplicationContext
  

  /**
   * Returns the Spring application context that contains this
   * application instance. It is the parent of the context returned
   * by {@link #getMainContext()}.
   */
  override def getParentContext() : ApplicationContext = mApplicationContext
  
  
  override def refreshConstraints() : Unit = {}

  override def refresh() : Unit = {}
 
  override def rebuild() : Unit = {}
  
  override def
  getResourceForClass
  (
    theClazz : Class[_]
  ) : Resource = null
  

  override def
  isArtefact
  (
    theClazz : Class[_]
  ) : Boolean = false
  
  override def
  isArtefactOfType
  (
    artefactType : String,
    theClazz : Class[_]
  ) : Boolean = false
  

  override def
  isArtefactOfType
  (
    artefactType : String,
    className : String
  ) : Boolean = false
  
  override def
  getArtefact
  (
    artefactType : String,
    name : String
  ) : GrailsClass = null
  

  override def
  getArtefactType
  (
    theClass : Class[_]
  ) : ArtefactHandler = null
  

  override def
  getArtefactInfo
  (
    artefactType : String
  ) : ArtefactInfo = null
  

  override def
  getArtefacts
  (
    artefactType : String
  ) : Array[GrailsClass] = Array()
  
  override def
  getArtefactForFeature
  (
    artefactType : String,
    featureID : Object
  ) : GrailsClass = null
  

  override def
  addArtefact
  (
    artefactType : String,
    artefactClass : Class[_]
  ) : GrailsClass = null
  
  override def
  addArtefact
  (
    artefactType : String,
    artefactGrailsClass : GrailsClass
  ) : GrailsClass = null
  

  override def
  registerArtefactHandler
  (
    handler : ArtefactHandler
  ) : Unit = {}
  

  override def
  hasArtefactHandler
  (
    typ : String
  ) : Boolean = false
  

  override def getArtefactHandlers() : Array[ArtefactHandler] = Array()
  

  override def initialise() : Unit = {}
  

  override def isInitialised() : Boolean = true
  

  override def getMetadata() : Metadata = null
  

  override def
  getArtefactByLogicalPropertyName
  (
    typ : String,
    logicalName : String
  ) : GrailsClass = null
  

  override def
  addArtefact
  (
    artefact : Class[_]
  ) : Unit = {}
  

  override def isWarDeployed() : Boolean = false
  

  override def
  addOverridableArtefact
  (
    artefact : Class[_]
  ) : Unit = {}
  
  override def configChanged() : Unit = {}
  

  override def
  getArtefactHandler
  (
    typ : String
  ) : ArtefactHandler = null
  
  override def
  setApplicationContext
  (
    appContext : ApplicationContext
  ) : Unit = 
  {
    mApplicationContext = appContext
  }
  
  private[this] var mApplicationContext : ApplicationContext = _  
}

object FakeGrailsApplication {
  
  val FLAT_CONFIG : JMap[String, Object] = Map[String, Object](
      "grails.web.disable.multipart" -> JBoolean.TRUE)
  
}
