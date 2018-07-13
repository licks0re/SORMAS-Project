package de.symeda.sormas.app;

import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.databinding.OnRebindCallback;
import android.databinding.ViewDataBinding;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.TextView;

import java.util.List;

import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.app.backend.common.AbstractDomainObject;
import de.symeda.sormas.app.core.INavigationCapsule;
import de.symeda.sormas.app.core.IUpdateSubHeadingTitle;
import de.symeda.sormas.app.core.NotImplementedException;
import de.symeda.sormas.app.core.NotificationContext;
import de.symeda.sormas.app.core.async.AsyncTaskResult;
import de.symeda.sormas.app.core.async.DefaultAsyncTask;
import de.symeda.sormas.app.core.async.TaskResultHolder;
import de.symeda.sormas.app.core.enumeration.IStatusElaborator;
import de.symeda.sormas.app.util.ConstantHelper;
import de.symeda.sormas.app.util.SoftKeyboardHelper;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public abstract class BaseEditFragment<TBinding extends ViewDataBinding, TData, TActivityRootData extends AbstractDomainObject> extends BaseFragment {

    public static final String TAG = BaseEditFragment.class.getSimpleName();

    private AsyncTask jobTask;
    private BaseEditActivity baseEditActivity;
    private IUpdateSubHeadingTitle subHeadingHandler;
    private NotificationContext notificationCommunicator;
    private TBinding contentViewStubBinding;
    private ViewDataBinding rootBinding;

    private View contentViewStubRoot;

    private boolean skipAfterLayoutBinding = false;
    private TActivityRootData activityRootData;
    private View rootView;

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveUserRightState(outState, editOrCreateUserRight);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        editOrCreateUserRight = getUserRightArg(savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();

        SoftKeyboardHelper.hideKeyboard(getActivity(), this);
    }

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        if (getActivity() instanceof BaseEditActivity) {
            this.baseEditActivity = (BaseEditActivity) this.getActivity();
        } else {
            throw new NotImplementedException("The edit activity for fragment must implement BaseEditActivity");
        }

        if (getActivity() instanceof IUpdateSubHeadingTitle) {
            this.subHeadingHandler = (IUpdateSubHeadingTitle) this.getActivity();
        } else {
            throw new NotImplementedException("Activity for fragment does not support updateSubHeadingTitle; "
                    + "implement IUpdateSubHeadingTitle");
        }

        if (getActivity() instanceof NotificationContext) {
            this.notificationCommunicator = (NotificationContext) this.getActivity();
        } else {
            throw new NotImplementedException("Activity for fragment does not support showErrorNotification; "
                    + "implement NotificationContext");
        }

        super.onCreateView(inflater, container, savedInstanceState);

        //Inflate Root
        rootBinding = DataBindingUtil.inflate(inflater, getRootEditLayout(), container, false);
        rootView = rootBinding.getRoot();

        final ViewStub vsChildFragmentFrame = (ViewStub) rootView.findViewById(R.id.vsChildFragmentFrame);
        vsChildFragmentFrame.setOnInflateListener(new ViewStub.OnInflateListener() {
            @Override
            public void onInflate(ViewStub stub, View inflated) {
                //onLayoutBindingHelper(stub, inflated);

                contentViewStubBinding = DataBindingUtil.bind(inflated);
                contentViewStubBinding.addOnRebindCallback(new OnRebindCallback() {
                    @Override
                    public void onBound(ViewDataBinding binding) {
                        super.onBound(binding);

                        if (!skipAfterLayoutBinding)
                            onAfterLayoutBinding(contentViewStubBinding);
                        skipAfterLayoutBinding = false;

                        getSubHeadingHandler().updateSubHeadingTitle(getSubHeadingTitle());
                    }
                });
                onLayoutBinding(contentViewStubBinding);
                contentViewStubRoot = contentViewStubBinding.getRoot();

                if (makeHeightMatchParent()) {
                    contentViewStubRoot.getLayoutParams().height = MATCH_PARENT;
                } else {
                    contentViewStubRoot.getLayoutParams().height = WRAP_CONTENT;
                }
            }
        });

        vsChildFragmentFrame.setLayoutResource(getEditLayout());


        jobTask = new DefaultAsyncTask(getContext()) {
            @Override
            public void onPreExecute() {
                getBaseActivity().showPreloader();
            }

            @Override
            public void doInBackground(final TaskResultHolder resultHolder) {
                prepareFragmentData(savedInstanceState);
            }

            @Override
            protected void onPostExecute(AsyncTaskResult<TaskResultHolder> taskResult) {
                getBaseActivity().hidePreloader();

                if (taskResult.getResultStatus().isFailed())
                    return;

                vsChildFragmentFrame.inflate();

                contentViewStubBinding.addOnRebindCallback(new OnRebindCallback() {
                    @Override
                    public void onBound(ViewDataBinding binding) {
                        super.onBound(binding);

                        if (!skipAfterLayoutBinding)
                            onAfterLayoutBinding(contentViewStubBinding);
                        skipAfterLayoutBinding = false;

                        getSubHeadingHandler().updateSubHeadingTitle(getSubHeadingTitle());
                    }
                });
            }
        }.executeOnThreadPool();

        return rootView;
    }

    public void showEmptyListHintWithAdd(List list, int entityNameResId) {
        showEmptyListHint(list, R.string.hint_no_records_found_add_new, entityNameResId);
    }

    public void showEmptyListHint(List list, int entityNameResId) {
        showEmptyListHint(list, R.string.hint_no_records_found, entityNameResId);
    }

    private void showEmptyListHint(List list, int stringFormatResId, int entityNameResId) {
        boolean isListEmpty = false;

        if (rootView == null)
            return;

        TextView emptyListHintView = (TextView) rootView.findViewById(R.id.emptyListHint);

        if (emptyListHintView == null)
            return;

        emptyListHintView.setVisibility(View.GONE);

        if (list == null || list.size() <= 0)
            isListEmpty = true;

        if (!isListEmpty)
            return;

        Resources r = getResources();

        String format = r.getString(stringFormatResId);

        emptyListHintView.setText(String.format(format, r.getString(entityNameResId)));
        emptyListHintView.setVisibility(View.VISIBLE);
    }

    public void requestLayoutRebind() {
        if (contentViewStubBinding != null) {
            onLayoutBinding(contentViewStubBinding);
        }
    }

    public int getRootEditLayout() {
        return R.layout.fragment_root_edit_layout;
    }

    public abstract int getEditLayout();

    public boolean makeHeightMatchParent() {
        return false;
    }

    public IUpdateSubHeadingTitle getSubHeadingHandler() {
        return this.subHeadingHandler;
    }

    public BaseEditActivity getBaseEditActivity() {
        return this.baseEditActivity;
    }

    protected String getSubHeadingTitle() {
        return null;
    }

    protected void setActivityRootData(TActivityRootData activityRootData) {
        this.activityRootData = activityRootData;
    }

    protected TActivityRootData getActivityRootData() {
        return this.activityRootData;
    }


    public abstract TData getPrimaryData();

    public ViewDataBinding getRootBinding() {
        return rootBinding;
    }

    public TBinding getContentBinding() {
        return contentViewStubBinding;
    }

    protected abstract void prepareFragmentData(Bundle savedInstanceState);

    protected abstract void onLayoutBinding(TBinding contentBinding);

    protected void onAfterLayoutBinding(TBinding contentBinding) {
    }

    protected static <TFragment extends BaseEditFragment, TCapsule extends INavigationCapsule> TFragment newInstance(Class<TFragment> f, TCapsule dataCapsule, AbstractDomainObject activityRootData) {

        TFragment fragment;
        try {
            fragment = f.newInstance();
        } catch (java.lang.InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Bundle bundle = fragment.getArguments();
        if (bundle == null) {
            bundle = new Bundle();
        }

        String dataUuid = dataCapsule.getRecordUuid();
        IStatusElaborator filterStatus = dataCapsule.getFilterStatus();
        IStatusElaborator pageStatus = dataCapsule.getPageStatus();
        int activeMenuKey = dataCapsule.getActiveMenuKey();
        String caseUuid = dataCapsule.getCaseUuid();
        String personUuid = dataCapsule.getPersonUuid();
        String eventUuid = dataCapsule.getEventUuid();
        String taskUuid = dataCapsule.getTaskUuid();
        String contactUuid = dataCapsule.getContactUuid();
        String sampleUuid = dataCapsule.getSampleUuid();
        Disease disease = dataCapsule.getDisease();
        boolean isForVisit = dataCapsule.isForVisit();
        boolean isVisitCooperative = dataCapsule.isVisitCooperative();
        UserRight userRight = dataCapsule.getUserRight();
        //AbstractDomainObject record = dataCapsule.getRecord();

        bundle.putInt(ConstantHelper.KEY_ACTIVE_MENU, activeMenuKey);
        bundle.putString(ConstantHelper.KEY_DATA_UUID, dataUuid);
        bundle.putString(ConstantHelper.KEY_PERSON_UUID, personUuid);
        bundle.putString(ConstantHelper.KEY_CASE_UUID, caseUuid);
        bundle.putString(ConstantHelper.KEY_EVENT_UUID, eventUuid);
        bundle.putString(ConstantHelper.KEY_TASK_UUID, taskUuid);
        bundle.putString(ConstantHelper.KEY_CONTACT_UUID, contactUuid);
        bundle.putString(ConstantHelper.KEY_SAMPLE_UUID, sampleUuid);
        bundle.putSerializable(ConstantHelper.ARG_DISEASE, disease);
        bundle.putBoolean(ConstantHelper.ARG_FOR_VISIT, isForVisit);
        bundle.putBoolean(ConstantHelper.ARG_VISIT_COOPERATIVE, isVisitCooperative);
        bundle.putSerializable(ConstantHelper.ARG_EDIT_OR_CREATE_USER_RIGHT, userRight);

        if (filterStatus != null)
            bundle.putSerializable(ConstantHelper.ARG_FILTER_STATUS, dataCapsule.getFilterStatus().getValue());

        if (pageStatus != null)
            bundle.putSerializable(ConstantHelper.ARG_PAGE_STATUS, pageStatus.getValue());

        fragment.setArguments(bundle);
        fragment.setActivityRootData(activityRootData);
        return fragment;
    }

    protected String getRecordUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_DATA_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_DATA_UUID);
            }
        }

        return result;
    }

    protected String getPersonUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_PERSON_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_PERSON_UUID);
            }
        }

        return result;
    }

    protected String getEventUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_EVENT_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_EVENT_UUID);
            }
        }

        return result;
    }

    protected String getTaskUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_TASK_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_TASK_UUID);
            }
        }

        return result;
    }

    protected String getContactUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_CONTACT_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_CONTACT_UUID);
            }
        }

        return result;
    }

    protected String getCaseUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_CASE_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_CASE_UUID);
            }
        }

        return result;
    }

    protected String getSampleUuidArg(Bundle arguments) {
        String result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.KEY_SAMPLE_UUID)) {
                result = (String) arguments.getString(ConstantHelper.KEY_SAMPLE_UUID);
            }
        }

        return result;
    }

    protected Disease getDiseaseArg(Bundle arguments) {
        Disease result = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.ARG_DISEASE)) {
                result = (Disease) arguments.getSerializable(ConstantHelper.ARG_DISEASE);
            }
        }

        return result;
    }

    protected boolean getForVisitArg(Bundle arguments) {
        boolean result = false;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.ARG_FOR_VISIT)) {
                result = (boolean) arguments.getBoolean(ConstantHelper.ARG_FOR_VISIT);
            }
        }

        return result;
    }

    protected boolean getVisitCooperativeArg(Bundle arguments) {
        boolean result = false;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.ARG_VISIT_COOPERATIVE)) {
                result = (boolean) arguments.getBoolean(ConstantHelper.ARG_VISIT_COOPERATIVE);
            }
        }

        return result;
    }

    protected UserRight getUserRightArg(Bundle arguments) {
        UserRight e = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.ARG_EDIT_OR_CREATE_USER_RIGHT)) {
                e = (UserRight) arguments.getSerializable(ConstantHelper.ARG_EDIT_OR_CREATE_USER_RIGHT);
            }
        }

        return e;
    }

    protected <E extends Enum<E>> E getFilterStatusArg(Bundle arguments) {
        E e = null;
        if (arguments != null && !arguments.isEmpty()) {
            if (arguments.containsKey(ConstantHelper.ARG_FILTER_STATUS)) {
                e = (E) arguments.getSerializable(ConstantHelper.ARG_FILTER_STATUS);
            }
        }

        return e;
    }

    protected void saveRecordUuidState(Bundle outState, String recordUuid) {
        if (outState != null) {
            outState.putString(ConstantHelper.KEY_DATA_UUID, recordUuid);
        }
    }

    protected void saveUserRightState(Bundle outState, UserRight userRight) {
        if (outState != null) {
            outState.putSerializable(ConstantHelper.ARG_EDIT_OR_CREATE_USER_RIGHT, userRight);
        }
    }

    public boolean isShowSaveAction() {
        return true;
    }

    public boolean isShowAddAction() {
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (jobTask != null && !jobTask.isCancelled())
            jobTask.cancel(true);
    }
}