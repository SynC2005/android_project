package com.example.sigma_chat_v2.network;

import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;


public class ApiClient {
    private static Retrofit retrofit = null;

    public static  Retrofit getClient(){
        if (retrofit == null){
            retrofit = new Retrofit.Builder()
                    .baseUrl("https://fcm.googleapis.com/")
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
