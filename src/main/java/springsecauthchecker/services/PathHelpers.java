package springsecauthchecker.services;

import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;

public class PathHelpers {

    public static String getClassPath(CtClass<?> clazz) {
        return clazz.getQualifiedName();
    }

    public static String getMethodPath(CtMethod<?> method) {
        return method.getDeclaringType().getQualifiedName() + "#" + method.getSignature();
    }

    public static String getAnnotationPath(CtAnnotation<?> annotation) {
        CtElement annotatedElement = annotation.getAnnotatedElement();
        String elementPath;

        if (annotatedElement instanceof CtClass) {
            elementPath = getClassPath((CtClass<?>) annotatedElement);
        } else if (annotatedElement instanceof CtMethod) {
            elementPath = getMethodPath((CtMethod<?>) annotatedElement);
        } else {
            elementPath = annotatedElement.toString();
        }

        return elementPath + "@" + annotation.getAnnotationType().getQualifiedName();
    }
}
