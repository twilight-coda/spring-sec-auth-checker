package services;

import models.StoreModel;

public interface StoreService {

    public void addUrl(String url, StoreModel storeModel);
    public StoreModel getStoreForUrl(String url);
    public StoreModel removeUrl(String url);

}