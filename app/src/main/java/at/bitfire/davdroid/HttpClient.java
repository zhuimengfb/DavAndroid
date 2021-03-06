/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import at.bitfire.dav4android.BasicDigestAuthHandler;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.Settings;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor();

    private static final String userAgent;
    static {
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(BuildConfig.buildTime));
        userAgent = "DAVdroid/" + BuildConfig.VERSION_NAME + " (" + date + "; dav4android; okhttp3) Android/" + Build.VERSION.RELEASE;
    }

    private HttpClient() {
    }

    public static OkHttpClient create(@Nullable Context context, @NonNull Account account, @NonNull final Logger logger) throws InvalidAccountException {
        OkHttpClient.Builder builder = defaultBuilder(context, logger);

        // use account settings for authentication
        AccountSettings settings = new AccountSettings(context, account);
        builder = addAuthentication(builder, null, settings.username(), settings.password());

        return builder.build();
    }

    public static OkHttpClient create(@NonNull Context context, @NonNull Logger logger) {
        return defaultBuilder(context, logger).build();
    }

    public static OkHttpClient create(@NonNull Context context, @NonNull Account account) throws InvalidAccountException {
        return create(context, account, App.log);
    }

    public static OkHttpClient create(@Nullable Context context) {
        return create(context, App.log);
    }


    private static OkHttpClient.Builder defaultBuilder(@Nullable Context context, @NonNull final Logger logger) {
        OkHttpClient.Builder builder = client.newBuilder();

        // use MemorizingTrustManager to manage self-signed certificates
        if (context != null) {
            App app = (App)context.getApplicationContext();
            if (App.getSslSocketFactoryCompat() != null && app.getCertManager() != null)
                builder.sslSocketFactory(App.getSslSocketFactoryCompat(), app.getCertManager());
            if (App.getHostnameVerifier() != null)
                builder.hostnameVerifier(App.getHostnameVerifier());
        }

        // set timeouts
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.writeTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(120, TimeUnit.SECONDS);

        // don't allow redirects, because it would break PROPFIND handling
        builder.followRedirects(false);

        // custom proxy support
        if (context != null) {
            SQLiteOpenHelper dbHelper = new ServiceDB.OpenHelper(context);
            try {
                Settings settings = new Settings(dbHelper.getReadableDatabase());
                if (settings.getBoolean(App.OVERRIDE_PROXY, false)) {
                    InetSocketAddress address = new InetSocketAddress(
                            settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT),
                            settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
                    );

                    Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                    builder.proxy(proxy);
                    App.log.log(Level.INFO, "Using proxy", proxy);
                }
            } catch(IllegalArgumentException|NullPointerException e) {
                App.log.log(Level.SEVERE, "Can't set proxy, ignoring", e);
            } finally {
                dbHelper.close();
            }
        }

        // add User-Agent to every request
        builder.addNetworkInterceptor(userAgentInterceptor);

        // add cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
        builder.cookieJar(new MemoryCookieStore());

        // add network logging, if requested
        if (logger.isLoggable(Level.FINEST)) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    logger.finest(message);
                }
            });
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);
        }

        return builder;
    }

    private static OkHttpClient.Builder addAuthentication(@NonNull OkHttpClient.Builder builder, @Nullable String host, @NonNull String username, @NonNull String password) {
        BasicDigestAuthHandler authHandler = new BasicDigestAuthHandler(host, username, password);
        return builder
                .addNetworkInterceptor(authHandler)
                .authenticator(authHandler);
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient client, @NonNull String username, @NonNull String password) {
        OkHttpClient.Builder builder = client.newBuilder();
        addAuthentication(builder, null, username, password);
        return builder.build();
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient client, @NonNull String host, @NonNull String username, @NonNull String password) {
        OkHttpClient.Builder builder = client.newBuilder();
        addAuthentication(builder, host, username, password);
        return builder.build();
    }


    static class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Locale locale = Locale.getDefault();
            Request request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", locale.getLanguage() + "-" + locale.getCountry() + ", " + locale.getLanguage() + ";q=0.7, *;q=0.5")
                    .build();
            return chain.proceed(request);
        }
    }

}
