package edge.jvm;

/** An annotation declared in a Java source, compiled together with the Kotlin sources that use it. */
public @interface JavaLabel {
    String value() default "";
}
