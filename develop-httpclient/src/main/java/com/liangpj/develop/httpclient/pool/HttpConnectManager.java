package com.liangpj.develop.httpclient.pool;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;

/**
 * 单线程-使用连接池管理HTTP请求
 * @author: liangpengju
 * @version: 1.0
 */
public class HttpConnectManager {

    public static void main(String[] args) throws Exception {
        //创建HTTP的连接池管理对象
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        //将最大连接数增加到200
        connectionManager.setMaxTotal(200);
        //将每个路由的默认最大连接数增加到20
        connectionManager.setDefaultMaxPerRoute(20);
        //将http://www.baidu.com:80的最大连接增加到50
        //HttpHost httpHost = new HttpHost("http://www.baidu.com",80);
        //connectionManager.setMaxPerRoute(new HttpRoute(httpHost),50);

        //发起3次GET请求
        String url ="https://www.baidu.com/s?word=java";
        long start = System.currentTimeMillis();
        for (int i=0;i<100;i++){
            doGet(connectionManager,url);
        }
        long end = System.currentTimeMillis();
        System.out.println("consume -> " + (end - start));

        //清理无效连接
        new IdleConnectionEvictor(connectionManager).start();
    }

    /**
     * 请求重试处理
     * @param tryTimes 重试次数
     * @return
     */
    public static HttpRequestRetryHandler retryHandler(final int tryTimes){

        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                // 如果已经重试了n次，就放弃
                if (executionCount >= tryTimes) {
                    return false;
                }
                // 如果服务器丢掉了连接，那么就重试
                if (exception instanceof NoHttpResponseException) {
                    return true;
                }
                // 不要重试SSL握手异常
                if (exception instanceof SSLHandshakeException) {
                    return false;
                }
                // 超时
                if (exception instanceof InterruptedIOException) {
                    return false;
                }
                // 目标服务器不可达
                if (exception instanceof UnknownHostException) {
                    return true;
                }
                // 连接被拒绝
                if (exception instanceof ConnectTimeoutException) {
                    return false;
                }
                // SSL握手异常
                if (exception instanceof SSLException) {
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext .adapt(context);
                HttpRequest request = clientContext.getRequest();
                // 如果请求是幂等的，就再次尝试
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }
                return false;
            }
        };
        return httpRequestRetryHandler;
    }

    /**
     * doGet
     * @param url 请求地址
     * @param connectionManager
     * @throws Exception
     */
    public static void doGet(HttpClientConnectionManager connectionManager,String url) throws Exception {
        //从连接池中获取client对象，多例
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRetryHandler(retryHandler(5)).build();

        // 创建http GET请求
        HttpGet httpGet = new HttpGet(url);
        // 构建请求配置信息
        RequestConfig config = RequestConfig.custom().setConnectTimeout(1000) // 创建连接的最长时间
                .setConnectionRequestTimeout(500) // 从连接池中获取到连接的最长时间
                .setSocketTimeout(10 * 1000) // 数据传输的最长时间10s
                .setStaleConnectionCheckEnabled(true) // 提交请求前测试连接是否可用
                .build();
        // 设置请求配置信息
        httpGet.setConfig(config);

        CloseableHttpResponse response = null;
        try {
            // 执行请求
            response = httpClient.execute(httpGet);
            // 判断返回状态是否为200
            if (response.getStatusLine().getStatusCode() == 200) {
                String content = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("内容长度：" + content.length());
            }
        } finally {
            if (response != null) {
                response.close();
            }
            // 此处不能关闭httpClient，如果关闭httpClient，连接池也会销毁
            // httpClient.close();
        }
    }

    /**
     * 监听连接池中空闲连接，清理无效连接
     */
    public static class IdleConnectionEvictor extends Thread {
        private final HttpClientConnectionManager connectionManager;
        private volatile boolean shutdown;
        public IdleConnectionEvictor(HttpClientConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }
        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(3000);//3s检查一次
                        connectionManager.closeExpiredConnections();// 关闭失效的连接
                    }
                }
            } catch (InterruptedException ex) {
                // 结束
                ex.printStackTrace();
            }
        }
        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
