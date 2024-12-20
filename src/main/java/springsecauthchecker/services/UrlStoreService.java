package springsecauthchecker.services;

import springsecauthchecker.models.StoreModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UrlStoreService implements StoreService {
    private final Map<String, StoreModel> urlStore = new HashMap<>();

    private UrlStoreService() { }

    public static UrlStoreService createUrlStoreService() {
        return new UrlStoreService();
    }

    @Override
    public void addUrl(String url, StoreModel storeModel) {
        urlStore.put(url, storeModel);
    }

    @Override
    public StoreModel getStoreForUrl(String url) {
        return urlStore.get(url);
    }

    @Override
    public StoreModel removeUrl(String url) {
        return urlStore.remove(url);
    }

    public List<StoreModel> getAllStoreModels() {
        return urlStore.values().stream().toList();
    }
}
