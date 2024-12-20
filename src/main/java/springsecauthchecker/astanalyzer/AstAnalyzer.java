package springsecauthchecker.astanalyzer;

import springsecauthchecker.SpringSecAuthChecker;
import springsecauthchecker.models.StoreModel;
import springsecauthchecker.models.StoreModelBuilder;
import springsecauthchecker.services.PathHelpers;
import springsecauthchecker.services.StoreService;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.AbstractFilter;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

import static springsecauthchecker.constants.HttpMethods.*;
import static springsecauthchecker.constants.PrePost.*;
import static springsecauthchecker.constants.SpringAnnotations.*;

public class AstAnalyzer {
    private final Launcher launcher;
    private final StoreService storeService;

    private final List<String> classAnnotations = Arrays.asList(CONTROLLER, REST_CONTROLLER);

    private final List<String> methodAnnotations = Arrays.asList( REQUEST_MAPPING,
            GET_MAPPING,
            POST_MAPPING,
            PUT_MAPPING,
            DELETE_MAPPING,
            PATCH_MAPPING
    );

    private final List<String> prePostAnnotations = Arrays.asList(
            PRE_AUTHORIZE,
            POST_AUTHORIZE,
            PRE_FILTER,
            POST_FILTER
    );


    public AstAnalyzer(String filePath, StoreService storeService) {
        this.storeService = storeService;
        this.launcher = new MavenLauncher(filePath,
                MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
    }

    public void analyzeAst() {
        CtModel model = this.launcher.getModel();
        List<CtClass<?>> classes = model.getElements(new AbstractFilter<>() {
            @Override
            public boolean matches(CtClass element) {
                return element != null
                        && element.getAnnotations()
                            .stream()
                            .anyMatch(ctAnnotation -> classAnnotations.contains(ctAnnotation.getName())
                        );
            }
        });
        for (CtClass<?> ctClass : classes) {
            Optional<CtAnnotation<?>> annotation = ctClass.getAnnotations().stream()
                    .filter(ctAnnotation -> ctAnnotation.getName().equals(REQUEST_MAPPING))
                    .findFirst();

            if (annotation.isEmpty()) continue;

            Optional<List<String>> urls = getRequestMappingValues(annotation.get());
            var controllerUrlFragments = urls.orElseGet(() -> List.of(""));

            for (String controllerUrlFragment : controllerUrlFragments) {
                List<CtMethod<?>> handlerMethods = ctClass.getMethods().stream()
                        .filter(ctMethod -> ctMethod.getAnnotations().stream()
                                .anyMatch(ctAnnotation -> methodAnnotations.contains(ctAnnotation.getName()))
                        ).toList();
                for (CtMethod<?> ctMethod : handlerMethods) {
                    var methodStores = processMethod(ctMethod);
                    methodStores.forEach(storeModel -> {
                        var methodUrl = storeModel.getUrl();
                        var separator = methodUrl.isEmpty() || methodUrl.charAt(0) == '/' ? "" : "/";
                        var url = controllerUrlFragment + separator + storeModel.getUrl();
                        storeModel.setUrl(url);
                        storeService.addUrl(storeModel.getUrl() + "|" + storeModel.getRequestMethod(), storeModel);
                    });
                }
            }
        }
    }

    private List<StoreModel> processMethod(CtMethod<?> ctMethod) {

        var prePostAnnotationValues = extractPrePostAnnotationExpressions(ctMethod);

        List<StoreModel> output = new ArrayList<>();
        List<CtAnnotation<?>> annotations = ctMethod.getAnnotations().stream()
                .filter(this::methodAnnotationIsToBeProcessed)
                .toList();

        for (CtAnnotation<?> annotation : annotations) {
            Optional<List<String>> urls = getRequestMappingValues(annotation);
            String extractedRequestMethod = extractRequestMethod(annotation);
            var methods = !extractedRequestMethod.isEmpty()
                    ? List.of(extractedRequestMethod)
                    : List.of(GET, POST, PUT, DELETE, PATCH);
            StoreModelBuilder storeBuilder = new StoreModelBuilder();

            if (urls.isPresent()) {
                for (var url : urls.get()) {
                    for (var method : methods) {
                        var storeModel = storeBuilder
                                .setUrl(url)
                                .setRequestMethod(method)
                                .setPreAuthorization(prePostAnnotationValues.getOrDefault(PRE_AUTHORIZE, ""))
                                .setPostAuthorization(prePostAnnotationValues.getOrDefault(POST_AUTHORIZE, ""))
                                .setPreFilter(prePostAnnotationValues.getOrDefault(PRE_FILTER, ""))
                                .setPostFilter(prePostAnnotationValues.getOrDefault(POST_FILTER, ""))
                                .createStoreModel();
                        output.add(storeModel);
                    }
                }
            } else {
                for (var method : methods) {
                    var storeModel = storeBuilder
                            .setUrl("")
                            .setRequestMethod(method)
                            .setPreAuthorization(prePostAnnotationValues.getOrDefault(PRE_AUTHORIZE, ""))
                            .setPostAuthorization(prePostAnnotationValues.getOrDefault(POST_AUTHORIZE, ""))
                            .setPreFilter(prePostAnnotationValues.getOrDefault(PRE_FILTER, ""))
                            .setPostFilter(prePostAnnotationValues.getOrDefault(POST_FILTER, ""))
                            .createStoreModel();
                    output.add(storeModel);
                }
            }
        }
        return output;
    }

    private Optional<List<String>> getRequestMappingValues(CtAnnotation<?> ctAnnotation) {
        var requestMapping = ctAnnotation.getValue("value");
        if (requestMapping instanceof CtNewArray<?> requestMappingUrls) {
            if (!requestMappingUrls.getElements().isEmpty()) {
                return Optional.of(requestMappingUrls.getElements().stream()
                        .filter(urlLiteral -> urlLiteral instanceof CtLiteral)
                        .map(urlLiteral -> (String) ((CtLiteral<?>) urlLiteral).getValue())
                        .collect(Collectors.toList()));
            } else {
                return Optional.empty();
            }
        } else if (requestMapping instanceof CtLiteral<?> requestMappingLiteral) {
            return Optional.of(Collections.singletonList((String) requestMappingLiteral.getValue()));
        } else {
            SpringSecAuthChecker.reportError(PathHelpers.getAnnotationPath(ctAnnotation),
                    "The value passed to request mapping is invalid");
            throw new IllegalStateException("The value passed to request mapping is invalid");
        }
    }

    private boolean methodAnnotationIsToBeProcessed(CtAnnotation<?> annotation) {
        String annotationName = annotation.getName();
        return methodAnnotations.contains(annotationName);
    }

    private String extractRequestMethod(CtAnnotation<?> ctAnnotation) {
        if (ctAnnotation.getName().equals(REQUEST_MAPPING)) {
            var methodValue = ctAnnotation.getValue("method");
            CtNewArray<?> requestMethodsArray = (CtNewArray<?>) methodValue;
            var elements = requestMethodsArray.getElements();
            if (elements.isEmpty()) return "";
            CtExpression<?> element = elements.getFirst();

            if (element instanceof CtFieldRead<?> fieldRead) {
                return fieldRead.getVariable().getSimpleName();
            } else {
                SpringSecAuthChecker.reportError(PathHelpers.getAnnotationPath(ctAnnotation),
                        "Unexpected request method");
                return null;
            }
        }

        String method = ctAnnotation.getName();
        return switch (method) {
            case GET_MAPPING -> GET;
            case POST_MAPPING -> POST;
            case PUT_MAPPING -> PUT;
            case DELETE_MAPPING -> DELETE;
            case PATCH_MAPPING -> PATCH;
            default -> {
                SpringSecAuthChecker.reportError(PathHelpers.getAnnotationPath(ctAnnotation),
                        "Unexpected request method");
                yield null;
            }
        };
    }

    private Map<String, String> extractPrePostAnnotationExpressions(CtMethod<?> ctMethod) {

        var methodAnnotations = ctMethod.getAnnotations();
        Map<String, String> output = new HashMap<>();

        for (CtAnnotation<? extends Annotation> ctAnnotation : methodAnnotations) {
            if (ctAnnotation.getName().equals(PRE_AUTHORIZE)
                    || ctAnnotation.getName().equals(POST_AUTHORIZE)
                    || ctAnnotation.getName().equals(PRE_FILTER)
                    || ctAnnotation.getName().equals(POST_FILTER)) {
                output.put(ctAnnotation.getName(), ctAnnotation.getValueAsString("value"));
            }
        }

        return output;
    }
}
