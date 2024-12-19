import astanalyzer.AstAnalyzer;
import services.UrlStoreService;

import java.util.InvalidPropertiesFormatException;

public class SpringSecAuthChecker {
    public static void main(String[] args) {
        UrlStoreService service = UrlStoreService.createUrlStoreService();
        AstAnalyzer astAnalyzer = new AstAnalyzer(
//                "D:\\Personal\\stuff\\java\\proper\\src\\main\\java",
//                "D:\\Coursework\\CS 700B\\spring-petclinic\\src\\main",
                "D:\\Coursework\\CS 700B\\fredbet",
//                new String[] {"D:\\Personal\\stuff\\java\\proper\\build\\classes\\java\\main"},
                new String[] {"D:\\Coursework\\CS 700B\\fredbet\\target\\classes"},
                service
        );
        try {
            astAnalyzer.analyzeAst();
            for (var storeModel : service.getAllStoreModels()) {
                System.out.println(storeModel);
            }
        } catch (InvalidPropertiesFormatException e) {
            throw new RuntimeException(e);
        }
    }
}
