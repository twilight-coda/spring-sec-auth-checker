package springsecauthchecker;

import springsecauthchecker.astanalyzer.AstAnalyzer;
import springsecauthchecker.services.UrlStoreService;


public class SpringSecAuthChecker {
    private static boolean hadError;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Error: Please provide the path as a command-line argument.");
            System.exit(1);
        }
        String path = args[0];
        runAstAnalysis(path);
    }

    public static void runAstAnalysis(String path) {
        UrlStoreService service = UrlStoreService.createUrlStoreService();
        AstAnalyzer astAnalyzer = new AstAnalyzer(
                path,
                service
        );
        astAnalyzer.analyzeAst();
        if (hadError) {
            System.exit(1);
        }
        for (var storeModel : service.getAllStoreModels()) {
            System.out.println(storeModel);
        }
    }

    public static void reportError(String path, String message) {
        System.err.printf("%n [" + path + "] Error" + ": " + message + "%n");
        hadError = true;
    }
}
