package com.trueaccord.scalapb.compiler

import com.google.protobuf.Descriptors._
import com.trueaccord.scalapb.Scalapb
import com.trueaccord.scalapb.Scalapb.{FieldOptions, ScalaPbOptions}

import scala.collection.JavaConversions._
import scala.collection.immutable.IndexedSeq

trait DescriptorPimps {
  def params: GeneratorParams

  val SCALA_RESERVED_WORDS = Set(
    "abstract", "case", "catch", "class", "def",
    "do", "else", "extends", "false", "final",
    "finally", "for", "forSome", "if", "implicit",
    "import", "lazy", "match", "new", "null",
    "object", "override", "package", "private", "protected",
    "return", "sealed", "super", "this", "throw",
    "trait", "try", "true", "type", "val",
    "var", "while", "with", "yield",
    "val", "var", "def", "if", "ne", "case")

  implicit class AsSymbolPimp(val s: String) {
    def asSymbol: String = if (SCALA_RESERVED_WORDS.contains(s)) s"`$s`" else s
  }

  private def snakeCaseToCamelCase(name: String, upperInitial: Boolean = false): String = {
    val b = new StringBuilder()
    @annotation.tailrec
    def inner(name: String, index: Int, capNext: Boolean): Unit = if (name.nonEmpty) {
      val (r, capNext2) = name.head match {
        case c if c.isLower => (Some(if (capNext) c.toUpper else c), false)
        case c if c.isUpper =>
          // force first letter to lower unless forced to capitalize it.
          (Some(if (index == 0 && !capNext) c.toLower else c), false)
        case c if c.isDigit => (Some(c), true)
        case _ => (None, true)
      }
      r.foreach(b.append)
      inner(name.tail, index + 1, capNext2)
    }
    inner(name, 0, upperInitial)
    b.toString
  }

  implicit class FieldDescriptorPimp(val fd: FieldDescriptor) {
    def containingOneOf: Option[OneofDescriptor] = Option(fd.getContainingOneof)

    def isInOneof: Boolean = containingOneOf.isDefined

    def scalaName: String = snakeCaseToCamelCase(fd.getName)

    def upperScalaName: String = snakeCaseToCamelCase(fd.getName, true)

    def fieldNumberConstantName: String = fd.getName.toUpperCase() + "_FIELD_NUMBER"

    def oneOfTypeName = {
      assert(isInOneof)
      fd.getContainingOneof.scalaTypeName + "." + upperScalaName
    }

    def baseScalaTypeName: String = {
      val base = baseSingleScalaTypeName
      if (fd.isOptional && !fd.isInOneof) s"Option[$base]"
      else if (fd.isRepeated) s"Seq[$base]"
      else base
    }

    def scalaTypeName: String = {
      val base = singleScalaTypeName
      if (fd.isOptional && !fd.isInOneof) s"Option[$base]"
      else if (fd.isRepeated) s"Seq[$base]"
      else base
    }

    def fieldOptions: FieldOptions = fd.getOptions.getExtension[FieldOptions](Scalapb.field)

    def customSingleScalaTypeName: Option[String] =
      if (fieldOptions.hasType) Some(fieldOptions.getType) else None

    def baseSingleScalaTypeName: String = fd.getJavaType match {
      case FieldDescriptor.JavaType.INT => "Int"
      case FieldDescriptor.JavaType.LONG => "Long"
      case FieldDescriptor.JavaType.FLOAT => "Float"
      case FieldDescriptor.JavaType.DOUBLE => "Double"
      case FieldDescriptor.JavaType.BOOLEAN => "Boolean"
      case FieldDescriptor.JavaType.BYTE_STRING => "com.google.protobuf.ByteString"
      case FieldDescriptor.JavaType.STRING => "String"
      case FieldDescriptor.JavaType.MESSAGE => fd.getMessageType.scalaTypeName
      case FieldDescriptor.JavaType.ENUM => fd.getEnumType.scalaTypeName
    }

    def singleScalaTypeName = customSingleScalaTypeName.getOrElse(baseSingleScalaTypeName)

    def getMethod = "get" + upperScalaName

    def typeMapperValName = "_typemapper_" + scalaName

    def typeMapper = fd.getContainingType.scalaTypeName + "." + typeMapperValName
  }

  implicit class OneofDescriptorPimp(val oneof: OneofDescriptor) {
    def scalaName = snakeCaseToCamelCase(oneof.getName)

    def upperScalaName = snakeCaseToCamelCase(oneof.getName, true)

    def fields: IndexedSeq[FieldDescriptor] = (0 until oneof.getFieldCount).map(oneof.getField)

    def scalaTypeName = oneof.getContainingType.scalaTypeName + "." + upperScalaName

    def empty = scalaTypeName + ".Empty"
  }

  implicit class MessageDescriptorPimp(val message: Descriptor) {
    def fields = message.getFields

    def fieldsWithoutOneofs = fields.filterNot(_.isInOneof)

    def scalaTypeName = message.getFile.fullScalaName(message.getFullName)

    def javaTypeName = message.getFile.fullJavaName(message.getFullName)
  }

  implicit class EnumDescriptorPimp(val enum: EnumDescriptor) {
    def scalaTypeName = enum.getFile.fullScalaName(enum.getFullName)

    def javaTypeName = enum.getFile.fullJavaName(enum.getFullName)
  }

  implicit class EnumValueDescriptorPimp(val enumValue: EnumValueDescriptor) {
    def objectName = allCapsToCamelCase(enumValue.getName, true)
  }

  implicit class FileDescriptorPimp(val file: FileDescriptor) {
    def scalaOptions: ScalaPbOptions = file.getOptions.getExtension[ScalaPbOptions](Scalapb.options)

    def javaPackage: String = {
      if (file.getOptions.hasJavaPackage)
        file.getOptions.getJavaPackage
      else file.getPackage
    }

    def javaPackageAsSymbol: String =
      javaPackage.split('.').map(_.asSymbol).mkString(".")

    def javaOuterClassName =
      if (file.getOptions.hasJavaOuterClassname)
        file.getOptions.getJavaOuterClassname
      else {
        snakeCaseToCamelCase(baseName(file.getName), true)
      }

    def scalaPackageName = {
      val requestedPackageName =
        if (scalaOptions.hasPackageName) scalaOptions.getPackageName
        else javaPackageAsSymbol

      if (scalaOptions.getFlatPackage || params.flatPackage)
        requestedPackageName
      else if (requestedPackageName.nonEmpty) requestedPackageName + "." + baseName(file.getName).asSymbol
      else baseName(file.getName).asSymbol
    }

    def javaFullOuterClassName = {
      val pkg = javaPackageAsSymbol
      if (pkg.isEmpty) javaOuterClassName
      else pkg + "." + javaOuterClassName
    }

    private def stripPackageName(fullName: String): String =
      if (file.getPackage.isEmpty) fullName
      else {
        assert(fullName.startsWith(file.getPackage + "."))
        fullName.substring(file.getPackage.size + 1)
      }

    def fullJavaName(fullName: String) = {
      javaFullOuterClassName + "." + stripPackageName(fullName)
    }

    def fullScalaName(fullName: String) = {
      val javaPkg = scalaPackageName
      val scalaName = stripPackageName(fullName).split('.').map(_.asSymbol).mkString(".")
      if (javaPkg.isEmpty) scalaName else (javaPkg + "." + scalaName)
    }

    def internalFieldsObjectName = "InternalFields_" + baseName(file.getName)
  }

  private def allCapsToCamelCase(name: String, upperInitial: Boolean = false): String = {
    val b = new StringBuilder()
    @annotation.tailrec
    def inner(name: String, capNext: Boolean): Unit = if (name.nonEmpty) {
      val (r, capNext2) = name.head match {
        case c if c.isUpper =>
          // capitalize according to capNext.
          (Some(if (capNext) c else c.toLower), false)
        case c if c.isLower =>
          // Lower caps never get capitalized, but will force
          // the next letter to be upper case.
          (Some(c), true)
        case c if c.isDigit => (Some(c), true)
        case _ => (None, true)
      }
      r.foreach(b.append)
      inner(name.tail, capNext2)
    }
    inner(name, upperInitial)
    b.toString
  }

  def baseName(fileName: String) =
    fileName.split("/").last.replaceAll(raw"[.]proto$$|[.]protodevel", "")

}
