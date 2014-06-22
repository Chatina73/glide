package com.bumptech.glide.load.engine;

import android.os.Handler;
import com.bumptech.glide.Resource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.cache.MemoryCache;
import com.bumptech.glide.request.ResourceCallback;

import java.util.ArrayList;
import java.util.List;

public class EngineJob implements ResourceCallback {
    private boolean isCacheable;
    private final EngineJobListener listener;
    private Key key;
    private MemoryCache cache;
    private Handler mainHandler;
    private List<ResourceCallback> cbs;
    private ResourceCallback cb;
    private boolean isCancelled;
    private boolean isComplete;

    public EngineJob(Key key, MemoryCache cache, Handler mainHandler, boolean isCacheable, EngineJobListener listener) {
        this.key = key;
        this.cache = cache;
        this.isCacheable = isCacheable;
        this.listener = listener;
        this.mainHandler = mainHandler;
    }

    public void addCallback(ResourceCallback cb) {
        if (this.cb == null) {
            this.cb = cb;
        } else {
            if (cbs == null) {
                cbs = new ArrayList<ResourceCallback>(2);
                cbs.add(this.cb);
            }
            cbs.add(cb);
        }
    }

    public void removeCallback(ResourceCallback cb) {
        if (cbs != null) {
            cbs.remove(cb);
            if (cbs.size() == 0) {
                cancel();
            }
        } else if (this.cb == cb) {
            this.cb = null;
            cancel();
        }
    }

    // Exposed for testing.
    void cancel() {
        if (isComplete || isCancelled) {
            return;
        }
        isCancelled = true;
        listener.onEngineJobCancelled(key);
    }

    // Exposed for testing.
    boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void onResourceReady(final Resource resource) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isCancelled) {
                    resource.recycle();
                    return;
                }
                isComplete = true;

                // Hold on to resource for duration of request so we don't recycle it in the middle of notifying if it
                // synchronously released by one of the callbacks.
                resource.acquire(1);
                listener.onEngineJobComplete(key);
                if (isCacheable) {
                    resource.acquire(1);
                    cache.put(key, resource);
                }
                if (cbs != null) {
                    resource.acquire(cbs.size());
                    for (ResourceCallback cb : cbs) {
                        cb.onResourceReady(resource);
                    }
                } else {
                    resource.acquire(1);
                    cb.onResourceReady(resource);
                }
                // Our request is complete, so we can release the resource.
                resource.release();
            }
        });
    }

    @Override
    public void onException(final Exception e) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isCancelled) {
                    return;
                }
                isComplete = true;

                listener.onEngineJobComplete(key);
                if (cbs != null) {
                    for (ResourceCallback cb : cbs) {
                        cb.onException(e);
                    }
                } else {
                    cb.onException(e);
                }
            }
        });
    }
}
