package de.symeda.sormas.app.dashboard.contact;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.symeda.sormas.app.BaseSummaryFragment;
import de.symeda.sormas.app.R;
import de.symeda.sormas.app.component.visualization.ViewTypeHelper;
import de.symeda.sormas.app.contact.landing.ContactsLandingSummaryAdapter;
import de.symeda.sormas.app.core.DashboardNavigationCapsule;
import de.symeda.sormas.app.core.IActivityCommunicator;

/**
 * Created by Orson on 08/04/2018.
 * <p>
 * www.technologyboard.org
 * sampson.orson@gmail.com
 * sampson.orson@technologyboard.org
 */
public class ContactSummaryFragment extends BaseSummaryFragment<ViewTypeHelper.ViewTypeEnum, ContactsLandingSummaryAdapter> {

    public static final String TAG = ContactSummaryFragment.class.getSimpleName();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        /*View view = inflater.inflate(R.layout.dashboard_summary_contact_layout, container, false);
        return view;*/
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected int getSectionTitleResId() {
        return R.string.dashboard_section_title_contact;
    }

    @Override
    protected ContactsLandingSummaryAdapter createSummaryAdapter() {
        return null;
    }

    @Override
    protected RecyclerView.LayoutManager createLayoutManager() {
        return null;
    }

    @Override
    protected int getContainerResId() {
        return R.id.fragment_frame_contact;
    }

    public static ContactSummaryFragment newInstance(IActivityCommunicator activityCommunicator, DashboardNavigationCapsule capsule) {
        try {
            return newInstance(activityCommunicator, ContactSummaryFragment.class, capsule);
        } catch (IllegalAccessException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (java.lang.InstantiationException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return null;
    }
}