package com.liangpj.develop.httpclient.pool;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import java.io.IOException;

/**
 * 多线程-HttpClient连接池管理HTTP请求实例
 */
public class MultiThreadHttpConnManager {
    public static void main(String[] args) {
        //连接池对象
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        //将最大连接数增加到200
        connectionManager.setMaxTotal(200);
        //将每个路由的默认最大连接数增加到20
        connectionManager.setDefaultMaxPerRoute(20);
        //HttpClient对象
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
        //URIs to DoGet
        String[] urisToGet = {
                "https://www.baidu.com/s?word=java",
                "https://www.baidu.com/s?word=java",
                "https://www.baidu.com/s?word=java",
                "https://www.baidu.com/s?word=java"
        };
        //为每一个URI创建一个线程
        GetThread[] threads = new GetThread[urisToGet.length];
        for (int i=0;i<threads.length;i++){
            HttpGet httpGet = new HttpGet(urisToGet[i]);
            threads[i] = new GetThread(httpClient,httpGet);
        }
        //启动线程
        for (int j=0;j<threads.length;j++){
            threads[j].start();
        }
        //join 线程
        for(int k=0;k<threads.length;k++){
            try {
                threads[k].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 执行Get请求线程
     */
    public static class GetThread extends Thread{
        private final CloseableHttpClient httpClient;
        private final HttpContext context;
        private final HttpGet httpget;
        public GetThread(CloseableHttpClient httpClient, HttpGet httpget) {
            this.httpClient = httpClient;
            this.context = HttpClientContext.create();
            this.httpget = httpget;
        }
        @Override
        public void run() {
            try {
                CloseableHttpResponse response = httpClient.execute(httpget,context);
                try {
                    HttpEntity entity = response.getEntity();
                }finally {
                    response.close();
                }
            }catch (ClientProtocolException ex){
                //处理客户端协议异常
            }catch (IOException ex){
                //处理客户端IO异常
            }
        }
    }
}
