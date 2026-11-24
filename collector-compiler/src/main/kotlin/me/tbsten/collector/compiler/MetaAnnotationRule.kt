package me.tbsten.collector.compiler

import org.jetbrains.kotlin.name.ClassId

/**
 * Annotation classes of the standard library carry no collected annotation: they are not looked into for
 * meta-annotations. One rule for FIR, which decides what is an item, and for IR, which lists the annotations of an item.
 */
internal fun ClassId.isNeverMetaAnnotation(): Boolean = packageFqName.asString().let { it == "kotlin" || it.startsWith("kotlin.") }
