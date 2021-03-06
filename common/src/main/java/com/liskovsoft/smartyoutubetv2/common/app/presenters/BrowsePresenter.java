package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.HeaderPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowsePresenter implements HeaderPresenter<BrowseView> {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    private static final long HEADER_REFRESH_PERIOD_MS = 120 * 60 * 1_000;
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final PlaybackPresenter mPlaybackPresenter;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private BrowseView mView;
    private final List<Header> mHeaders;
    private final Map<Integer, Observable<MediaGroup>> mGridMapping;
    private final Map<Integer, Observable<List<MediaGroup>>> mRowMapping;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Disposable mSignCheckAction;
    private long mCurrentHeaderId = -1;
    private long mLastUpdateTimeMs;

    private BrowsePresenter(Context context) {
        GlobalPreferences.instance(context); // auth token storage
        mContext = context;
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
        mHeaders = new ArrayList<>();
        mGridMapping = new HashMap<>();
        mRowMapping = new HashMap<>();
        initHeaders();
    }

    public static BrowsePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BrowsePresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mView == null) {
            return;
        }

        addHeaders();
    }

    private void initHeaders() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mHeaders.add(new Header(MediaGroup.TYPE_HOME, mContext.getString(R.string.header_home), Header.TYPE_ROW, R.drawable.icon_home));
        mHeaders.add(new Header(MediaGroup.TYPE_GAMING, mContext.getString(R.string.header_gaming), Header.TYPE_ROW, R.drawable.icon_gaming));
        mHeaders.add(new Header(MediaGroup.TYPE_NEWS, mContext.getString(R.string.header_news), Header.TYPE_ROW, R.drawable.icon_news));
        mHeaders.add(new Header(MediaGroup.TYPE_MUSIC, mContext.getString(R.string.header_music), Header.TYPE_ROW, R.drawable.icon_music));
        mHeaders.add(new Header(MediaGroup.TYPE_SUBSCRIPTIONS, mContext.getString(R.string.header_subscriptions), Header.TYPE_GRID, R.drawable.icon_subscriptions, true));
        mHeaders.add(new Header(MediaGroup.TYPE_HISTORY, mContext.getString(R.string.header_history), Header.TYPE_GRID, R.drawable.icon_history, true));
        mHeaders.add(new Header(MediaGroup.TYPE_PLAYLISTS, mContext.getString(R.string.header_playlists), Header.TYPE_ROW, R.drawable.icon_playlist, true));
        mHeaders.add(new Header(MediaGroup.TYPE_SETTINGS, mContext.getString(R.string.header_settings), Header.TYPE_TEXT_GRID, R.drawable.icon_settings));

        mRowMapping.put(MediaGroup.TYPE_HOME, mediaGroupManager.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mediaGroupManager.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mediaGroupManager.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mediaGroupManager.getGamingObserve());
        mRowMapping.put(MediaGroup.TYPE_PLAYLISTS, mediaGroupManager.getPlaylistsObserve());

        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mediaGroupManager.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mediaGroupManager.getHistoryObserve());
    }

    private void addHeaders() {
        for (Header header : mHeaders) {
            addHeader(header);
        }
    }

    private void addHeader(Header header) {
        mView.updateHeader(VideoGroup.from(header));
    }

    @Override
    public void register(BrowseView view) {
        mView = view;
    }

    @Override
    public void unregister(BrowseView view) {
        mView = null;
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (mView == null) {
            return;
        }

        if (item.isVideo()) {
            mPlaybackPresenter.openVideo(item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(mContext).openChannel(item);
        }

        updateRefreshTime();
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (mView == null) {
            return;
        }

        VideoMenuPresenter.instance(mContext).showMenu(item);
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd. Group title: " + group.getTitle());

        boolean updateInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (updateInProgress) {
            return;
        }

        continueGroup(group);
    }

    @Override
    public void onViewResumed() {
        long timeAfterPauseMs = System.currentTimeMillis() - mLastUpdateTimeMs;
        if (timeAfterPauseMs > HEADER_REFRESH_PERIOD_MS) { // update header every n minutes
            refresh();
        }
    }

    @Override
    public void onHeaderFocused(long headerId) {
        updateHeader(headerId);
    }

    public void refresh() {
        updateHeader(mCurrentHeaderId);
    }

    private void updateRefreshTime() {
        mLastUpdateTimeMs = System.currentTimeMillis();
    }

    private void updateHeader(long headerId) {
        mCurrentHeaderId = headerId;

        if (headerId == -1 || mView == null) {
            return;
        }

        RxUtils.disposeActions(mUpdateAction, mScrollAction, mSignCheckAction);

        for (Header header : mHeaders) {
            if (header.getId() == headerId) {
                mView.showProgressBar(true);
                mView.clearHeader(header);
                updateHeader(header);
            }
        }
    }

    private void updateHeader(Header header) {
        switch (header.getType()) {
            case Header.TYPE_GRID:
                Observable<MediaGroup> group = mGridMapping.get(header.getId());
                updateGridHeader(header, group, header.isAuthOnly());
                break;
            case Header.TYPE_ROW:
                Observable<List<MediaGroup>> groups = mRowMapping.get(header.getId());
                updateRowsHeader(header, groups, header.isAuthOnly());
                break;
        }
    }

    private void updateRowsHeader(Header header, Observable<List<MediaGroup>> groups, boolean authCheck) {
        Log.d(TAG, "loadRowsHeader: Start loading header: " + header.getTitle());

        authCheck(authCheck, () -> updateRowsHeader(header, groups));
    }

    private void updateGridHeader(Header header, Observable<MediaGroup> group, boolean authCheck) {
        Log.d(TAG, "loadGridHeader: Start loading header: " + header.getTitle());

        authCheck(authCheck, () -> updateGridHeader(header, group));
    }

    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mScrollAction = mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueMediaGroup -> mView.updateHeader(VideoGroup.from(continueMediaGroup, group.getHeader()))
                        , error -> Log.e(TAG, "continueGroup error: " + error)
                        , () -> mView.showProgressBar(false));
    }

    private void authCheck(boolean check, Runnable callback) {
        if (!check) {
            callback.run();
            return;
        }

        SignInManager signInManager = mMediaService.getSignInManager();

        mSignCheckAction = signInManager.isSignedObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isSigned -> {
                            if (isSigned) {
                                callback.run();
                            } else {
                                mView.updateErrorIfEmpty(new SignInError(mContext));
                                mView.showProgressBar(false);
                            }
                        }
                );
                
    }

    private void updateRowsHeader(Header header, Observable<List<MediaGroup>> groups) {
        Log.d(TAG, "updateRowsHeader: Start loading header: " + header.getTitle());

        mUpdateAction = groups
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroups -> updateRowsHeader(header, mediaGroups)
                        , error -> Log.e(TAG, "updateRowsHeader error: " + error)
                        , () -> {
                            mView.showProgressBar(false);
                            mView.updateErrorIfEmpty(new CategoryEmptyError(mContext));
                        });
    }

    private void updateRowsHeader(Header header, List<MediaGroup> mediaGroups) {
        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            mView.updateHeader(VideoGroup.from(mediaGroup, header));

            updateRefreshTime();
        }
    }

    private void updateGridHeader(Header header, Observable<MediaGroup> group) {
        Log.d(TAG, "updateGridHeader: Start loading header: " + header.getTitle());

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> {
                            mView.updateHeader(VideoGroup.from(mediaGroup, header));
                            updateRefreshTime();
                        }
                        , error -> Log.e(TAG, "updateGridHeader error: " + error)
                        , () -> {
                            mView.showProgressBar(false);
                            mView.updateErrorIfEmpty(new CategoryEmptyError(mContext));
                        });
    }
}
