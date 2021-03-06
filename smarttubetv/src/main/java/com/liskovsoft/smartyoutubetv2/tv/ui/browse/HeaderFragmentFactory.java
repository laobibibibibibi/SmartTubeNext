package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment.OnHeaderViewSelectedListener;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.group.HeaderGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.group.HeaderRowsFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.group.VideoGroupFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings.HeaderTextGridFragment;

import java.util.ArrayList;
import java.util.List;

public class HeaderFragmentFactory extends BrowseSupportFragment.FragmentFactory<Fragment> {
    private static final String TAG = HeaderFragmentFactory.class.getSimpleName();
    private final OnHeaderViewSelectedListener mViewSelectedListener;
    private final List<VideoGroup> mCachedData;
    private Fragment mCurrentFragment;

    public HeaderFragmentFactory() {
        this(null);
    }

    public HeaderFragmentFactory(OnHeaderViewSelectedListener viewSelectedListener) {
        mViewSelectedListener = viewSelectedListener;
        mCachedData = new ArrayList<>();
    }

    /**
     * Called each time when header is changed.<br/>
     * So, no need to clear state.
     */
    @Override
    public Fragment createFragment(Object rowObj) {
        Log.d(TAG, "Creating PageRow fragment");

        Row row = (Row) rowObj;

        HeaderItem header = row.getHeaderItem();
        Fragment fragment = null;

        if (header instanceof FragmentHeaderItem) {
            int type = ((FragmentHeaderItem) header).getType();

            if (type == Header.TYPE_ROW) {
                fragment = new HeaderRowsFragment();
            } else if (type == Header.TYPE_GRID) {
                fragment = new HeaderGridFragment();
            } else if (type == Header.TYPE_TEXT_GRID) {
                fragment = new HeaderTextGridFragment();
            }
        }

        if (fragment != null) {
            mCurrentFragment = fragment;

            // give a chance to clear pending updates
            if (mViewSelectedListener != null) {
                mViewSelectedListener.onHeaderSelected(null, row);
            }

            updateFromCache(fragment);

            return fragment;
        }

        throw new IllegalArgumentException(String.format("Invalid row %s", rowObj));
    }

    public void updateFragment(VideoGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        if (mCurrentFragment == null) {
            Log.e(TAG, "Page row fragment not initialized for group: " + group.getTitle());
            return;
        }

        mCachedData.add(group);

        updateFragment(mCurrentFragment, group);
    }

    private void updateFragment(Fragment fragment, VideoGroup group) {
        if (fragment instanceof VideoGroupFragment) {
            ((VideoGroupFragment) fragment).update(group);
        } else {
            Log.e(TAG, "updateFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    private void updateFromCache(Fragment fragment) {
        for (VideoGroup group : mCachedData) {
            updateFragment(fragment, group);
        }
    }

    public void clearFragment() {
        mCachedData.clear();

        if (mCurrentFragment != null) {
            clearFragment(mCurrentFragment);
        }
    }

    private void clearFragment(Fragment fragment) {
        if (fragment instanceof VideoGroupFragment) {
            ((VideoGroupFragment) fragment).invalidate();
        } else {
            Log.e(TAG, "clearFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    public boolean isEmpty() {
        if (mCurrentFragment instanceof VideoGroupFragment) {
            return ((VideoGroupFragment) mCurrentFragment).isEmpty();
        }

        return false;
    }

    public Fragment getCurrentFragment() {
        return mCurrentFragment;
    }
}
