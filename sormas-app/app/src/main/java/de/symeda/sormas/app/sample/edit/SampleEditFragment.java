package de.symeda.sormas.app.sample.edit;

import android.content.res.Resources;
import android.databinding.ObservableArrayList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import java.util.Date;
import java.util.List;

import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.facility.FacilityDto;
import de.symeda.sormas.api.sample.SampleMaterial;
import de.symeda.sormas.api.sample.SampleSource;
import de.symeda.sormas.api.sample.SampleTestType;
import de.symeda.sormas.api.sample.SpecimenCondition;
import de.symeda.sormas.api.utils.DataHelper;
import de.symeda.sormas.app.BaseEditActivityFragment;
import de.symeda.sormas.app.R;
import de.symeda.sormas.app.backend.common.DaoException;
import de.symeda.sormas.app.backend.common.DatabaseHelper;
import de.symeda.sormas.app.backend.config.ConfigProvider;
import de.symeda.sormas.app.backend.facility.Facility;
import de.symeda.sormas.app.backend.sample.Sample;
import de.symeda.sormas.app.backend.sample.SampleDao;
import de.symeda.sormas.app.backend.sample.SampleTest;
import de.symeda.sormas.app.component.Item;
import de.symeda.sormas.app.component.OnLinkClickListener;
import de.symeda.sormas.app.component.OnTeboSwitchCheckedChangeListener;
import de.symeda.sormas.app.component.controls.TeboSpinner;
import de.symeda.sormas.app.component.controls.TeboSwitch;
import de.symeda.sormas.app.component.VisualState;
import de.symeda.sormas.app.core.BoolResult;
import de.symeda.sormas.app.core.IActivityCommunicator;
import de.symeda.sormas.app.core.IEntryItemOnClickListener;
import de.symeda.sormas.app.core.NotificationContext;
import de.symeda.sormas.app.core.ISaveable;
import de.symeda.sormas.app.core.YesNo;
import de.symeda.sormas.app.core.async.IJobDefinition;
import de.symeda.sormas.app.core.async.ITaskExecutor;
import de.symeda.sormas.app.core.async.ITaskResultCallback;
import de.symeda.sormas.app.core.async.ITaskResultHolderIterator;
import de.symeda.sormas.app.core.async.TaskExecutorFor;
import de.symeda.sormas.app.core.async.TaskResultHolder;
import de.symeda.sormas.app.core.notification.NotificationHelper;
import de.symeda.sormas.app.core.notification.NotificationType;
import de.symeda.sormas.app.databinding.FragmentSampleEditLayoutBinding;
import de.symeda.sormas.app.rest.RetroProvider;
import de.symeda.sormas.app.rest.SynchronizeDataAsync;
import de.symeda.sormas.app.shared.SampleFormNavigationCapsule;
import de.symeda.sormas.app.shared.ShipmentStatus;
import de.symeda.sormas.app.util.DataUtils;
import de.symeda.sormas.app.util.ErrorReportingHelper;
import de.symeda.sormas.app.util.SyncCallback;
import de.symeda.sormas.app.validation.SampleValidator;

/**
 * Created by Orson on 05/02/2018.
 * <p>
 * www.technologyboard.org
 * sampson.orson@gmail.com
 * sampson.orson@technologyboard.org
 */

public class SampleEditFragment extends BaseEditActivityFragment<FragmentSampleEditLayoutBinding, Sample, Sample> implements OnTeboSwitchCheckedChangeListener, ISaveable {

    private AsyncTask onResumeTask;
    private AsyncTask saveSample;

    private String caseUuid = null;
    private String recordUuid = null;
    private String sampleMaterial = null;
    private ShipmentStatus pageStatus;
    private Sample record;
    private IEntryItemOnClickListener onRecentTestItemClickListener;
    private int mLastCheckedId = -1;

    private List<Item> sampleMaterialList;
    private List<Item> testTypeList;
    private List<Item> sampleSourceList;
    private List<Facility> labList;

    private OnLinkClickListener referralLinkCallback;
    private SampleTest mostRecentTest;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        savePageStatusState(outState, pageStatus);
        saveRecordUuidState(outState, recordUuid);
        saveCaseUuidState(outState, caseUuid);
        saveSampleMaterialState(outState, sampleMaterial);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = (savedInstanceState != null)? savedInstanceState : getArguments();

        recordUuid = getRecordUuidArg(arguments);
        caseUuid = getCaseUuidArg(arguments);
        sampleMaterial = getSampleMaterialArg(arguments);
        pageStatus = (ShipmentStatus) getPageStatusArg(arguments);
    }

    @Override
    protected String getSubHeadingTitle() {
        Resources r = getResources();
        return r.getString(R.string.caption_sample_information);
    }

    @Override
    public Sample getPrimaryData() {
        return record;
    }

    @Override
    public boolean onBeforeLayoutBinding(Bundle savedInstanceState, TaskResultHolder resultHolder, BoolResult resultStatus, boolean executionComplete) {
        if (!executionComplete) {
            SampleTest sampleTest = null;
            Sample sample = getActivityRootData();

            if (sample != null) {
                if (sample.isUnreadOrChildUnread())
                    DatabaseHelper.getSampleDao().markAsRead(sample);

                sampleTest = DatabaseHelper.getSampleTestDao().queryMostRecentBySample(sample);
            }

            resultHolder.forItem().add(sample);
            resultHolder.forItem().add(sampleTest);

            resultHolder.forOther().add(DataUtils.getEnumItems(SampleMaterial.class, false));
            resultHolder.forOther().add(DataUtils.getEnumItems(SampleTestType.class, false));
            resultHolder.forOther().add(DataUtils.getEnumItems(SampleSource.class, false));

            resultHolder.forList().add(DatabaseHelper.getFacilityDao().getLaboratories(true));
        } else {
            ITaskResultHolderIterator itemIterator = resultHolder.forItem().iterator();
            ITaskResultHolderIterator listIterator = resultHolder.forList().iterator();
            ITaskResultHolderIterator otherIterator = resultHolder.forOther().iterator();

            //Item Data
            if (itemIterator.hasNext())
                record =  itemIterator.next();

            if (record == null)
                getActivity().finish();

            if (itemIterator.hasNext())
                mostRecentTest = itemIterator.next();

            if (listIterator.hasNext())
                labList =  listIterator.next();

            if (otherIterator.hasNext())
                sampleMaterialList =  otherIterator.next();

            if (otherIterator.hasNext())
                testTypeList =  otherIterator.next();

            if (otherIterator.hasNext())
                sampleSourceList =  otherIterator.next();

            setupCallback();
        }

        return true;
    }

    @Override
    public void onLayoutBinding(FragmentSampleEditLayoutBinding contentBinding) {
        if (record == null)
            return;

        if (!record.isShipped()) {
            contentBinding.dtpShipmentDate.setVisibility(View.GONE);
            contentBinding.txtShipmentDetails.setVisibility(View.GONE);
        }

        if (record.getSampleMaterial() == SampleMaterial.OTHER) {
            contentBinding.txtSampleMaterialText.setVisibility(View.INVISIBLE);
        }

        if (record.getLab().getUuid().equals(FacilityDto.OTHER_LABORATORY_UUID)) {
            contentBinding.txtLaboratoryDetails.setVisibility(View.VISIBLE);
        }

        if (record.getAssociatedCase().getDisease() != Disease.NEW_INFLUENCA) {
            contentBinding.spnSampleSource.setVisibility(View.GONE);
        }

        if (record.isReceived()) {
            contentBinding.sampleReceivedLayout.setVisibility(View.VISIBLE);
        }

        if (record.getSpecimenCondition() != SpecimenCondition.NOT_ADEQUATE) {
            contentBinding.recentTestLayout.setVisibility(View.VISIBLE);
            if (mostRecentTest != null) {
                contentBinding.spnTestType.setVisibility(View.VISIBLE);
                //contentBinding.sampleTestResult.setVisibility(View.VISIBLE);
            } else {
                contentBinding.sampleNoRecentTestText.setVisibility(View.VISIBLE);
            }
        }

        // only show referred to field when there is a referred sample
        if (record.getReferredTo() != null) {
            final Sample referredSample = record.getReferredTo();
            contentBinding.txtReferredTo.setVisibility(View.VISIBLE);
            contentBinding.txtReferredTo.setValue(getActivity().getResources().getString(R.string.sample_referred_to) + " " + referredSample.getLab().toString() + " " + "\u279D");
        } else {
            contentBinding.txtReferredTo.setVisibility(View.GONE);
        }

        //TODO: Set required hints for sample data
        SampleValidator.setRequiredHintsForSampleData(contentBinding);

        contentBinding.setData(record);
        contentBinding.setCaze(record.getAssociatedCase());
        contentBinding.setLab(record.getLab());
        contentBinding.setResults(getTestResults());
        contentBinding.setRecentTestItemClickCallback(onRecentTestItemClickListener);

        contentBinding.setYesNoClass(YesNo.class);
        contentBinding.setShippedYesCallback(this);
        contentBinding.setReferralLinkCallback(referralLinkCallback);
    }

    @Override
    public void onAfterLayoutBinding(final FragmentSampleEditLayoutBinding contentBinding) {
        contentBinding.spnTestType.initialize(new TeboSpinner.ISpinnerInitSimpleConfig() {
            @Override
            public Object getSelectedValue() {
                return null;
            }

            @Override
            public List<Item> getDataSource(Object parentValue) {
                return (testTypeList.size() > 0) ? DataUtils.addEmptyItem(testTypeList)
                        : testTypeList;
            }

            @Override
            public VisualState getInitVisualState() {
                return null;
            }
        });

        contentBinding.spnSampleSource.initialize(new TeboSpinner.ISpinnerInitSimpleConfig() {
            @Override
            public Object getSelectedValue() {
                return null;
            }

            @Override
            public List<Item> getDataSource(Object parentValue) {
                return (sampleSourceList.size() > 0) ? DataUtils.addEmptyItem(sampleSourceList)
                        : sampleSourceList;
            }

            @Override
            public VisualState getInitVisualState() {
                return null;
            }
        });

        contentBinding.spnSampleMaterial.initialize(new TeboSpinner.ISpinnerInitConfig() {
            @Override
            public Object getSelectedValue() {
                return null;
            }

            @Override
            public List<Item> getDataSource(Object parentValue) {
                return (sampleMaterialList.size() > 0) ? DataUtils.addEmptyItem(sampleMaterialList)
                        : sampleMaterialList;
            }

            @Override
            public VisualState getInitVisualState() {
                return null;
            }

            @Override
            public void onItemSelected(TeboSpinner view, Object value, int position, long id) {
                SampleMaterial material = (SampleMaterial)value;

                if (material == SampleMaterial.OTHER) {
                    contentBinding.txtSampleMaterialText.setVisibility(View.VISIBLE);
                } else {
                    contentBinding.txtSampleMaterialText.setVisibility(View.INVISIBLE);
                    contentBinding.txtSampleMaterialText.setValue("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        contentBinding.spnLaboratory.initialize(new TeboSpinner.ISpinnerInitConfig() {
            @Override
            public Object getSelectedValue() {
                return null;
            }

            @Override
            public List<Item> getDataSource(Object parentValue) {
                return (labList.size() > 0) ? DataUtils.toItems(labList)
                        : DataUtils.toItems(labList, false);
            }

            @Override
            public void onItemSelected(TeboSpinner view, Object value, int position, long id) {
                Facility laboratory = (Facility) value;

                if (laboratory.getUuid().equals(FacilityDto.OTHER_LABORATORY_UUID)) {
                    contentBinding.txtLaboratoryDetails.setVisibility(View.VISIBLE);
                } else {
                    contentBinding.txtLaboratoryDetails.setVisibility(View.GONE);
                    contentBinding.txtLaboratoryDetails.setValue("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }

            @Override
            public VisualState getInitVisualState() {
                return null;
            }
        });

        contentBinding.dtpDateAndTimeOfSampling.setFragmentManager(getFragmentManager());
        contentBinding.dtpShipmentDate.setFragmentManager(getFragmentManager());

        //TODO: Properly disable Tebo controls
        if (!ConfigProvider.getUser().getUuid().equals(record.getReportingUser().getUuid())) {
            contentBinding.txtSampleCode.changeVisualState(VisualState.DISABLED);
            contentBinding.dtpDateAndTimeOfSampling.changeVisualState(VisualState.DISABLED);
            contentBinding.dtpDateAndTimeOfSampling.changeVisualState(VisualState.DISABLED);
            contentBinding.spnSampleMaterial.changeVisualState(VisualState.DISABLED);
            contentBinding.txtSampleMaterialText.changeVisualState(VisualState.DISABLED);
            contentBinding.spnTestType.changeVisualState(VisualState.DISABLED);
            contentBinding.spnLaboratory.changeVisualState(VisualState.DISABLED);
            contentBinding.txtLaboratoryDetails.changeVisualState(VisualState.DISABLED);
            contentBinding.swhShipped.changeVisualState(VisualState.DISABLED);
            contentBinding.dtpShipmentDate.changeVisualState(VisualState.DISABLED);
            contentBinding.txtShipmentDetails.changeVisualState(VisualState.DISABLED);
        }

    }

    @Override
    protected void updateUI(FragmentSampleEditLayoutBinding contentBinding, Sample sample) {
        contentBinding.spnSampleMaterial.setValue(sample.getSampleMaterial(), true);
        contentBinding.spnTestType.setValue(sample.getSuggestedTypeOfTest(), true);
        contentBinding.spnLaboratory.setValue(sample.getLab(), true);
    }

    @Override
    public void onPageResume(FragmentSampleEditLayoutBinding contentBinding, boolean hasBeforeLayoutBindingAsyncReturn) {
        if (!hasBeforeLayoutBindingAsyncReturn)
            return;

        try {
            ITaskExecutor executor = TaskExecutorFor.job(new IJobDefinition() {
                @Override
                public void preExecute(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    //getActivityCommunicator().showPreloader();
                    //getActivityCommunicator().hideFragmentView();
                }

                @Override
                public void execute(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    SampleTest sampleTest = null;
                    Sample sample = getActivityRootData();

                    if (sample != null) {
                        if (sample.isUnreadOrChildUnread())
                            DatabaseHelper.getSampleDao().markAsRead(sample);

                        sampleTest = DatabaseHelper.getSampleTestDao().queryMostRecentBySample(sample);
                    }

                    resultHolder.forItem().add(sample);
                    resultHolder.forItem().add(sampleTest);
                }
            });
            onResumeTask = executor.execute(new ITaskResultCallback() {
                @Override
                public void taskResult(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    //getActivityCommunicator().hidePreloader();
                    //getActivityCommunicator().showFragmentView();

                    if (resultHolder == null){
                        return;
                    }

                    ITaskResultHolderIterator itemIterator = resultHolder.forItem().iterator();

                    if (itemIterator.hasNext())
                        record = itemIterator.next();

                    if (itemIterator.hasNext())
                        mostRecentTest = itemIterator.next();

                    if (record != null)
                        requestLayoutRebind();
                    else {
                        getActivity().finish();
                    }
                }
            });
        } catch (Exception ex) {
            //getActivityCommunicator().hidePreloader();
            //getActivityCommunicator().showFragmentView();
        }

    }

    @Override
    public int getEditLayout() {
        return R.layout.fragment_sample_edit_layout;
    }

    private ObservableArrayList getTestResults() {
        ObservableArrayList results = new ObservableArrayList();

        if (mostRecentTest != null)
            results.add(mostRecentTest);

        return results;
    }

    private void setupCallback() {
        onRecentTestItemClickListener = new IEntryItemOnClickListener() {
            @Override
            public void onClick(View v, Object item) {

            }
        };

        referralLinkCallback = new OnLinkClickListener() {
            @Override
            public void onClick(View v, Object item) {
                if (record != null && record.getReferredTo() != null) {
                    Sample referredSample = record.getReferredTo();
                    String sampleMaterial = record.getSampleMaterialText();

                    SampleFormNavigationCapsule dataCapsule = (SampleFormNavigationCapsule)new SampleFormNavigationCapsule(getContext(),
                            referredSample.getUuid(), pageStatus)
                            .setSampleMaterial(sampleMaterial);
                    SampleEditActivity.goToActivity(getActivity(), dataCapsule);
                }
            }
        };
    }

    @Override
    public boolean includeFabNonOverlapPadding() {
        return false;
    }

    @Override
    public boolean showSaveAction() {
        return true;
    }

    @Override
    public boolean showAddAction() {
        return false;
    }

    @Override
    public void onCheckedChanged(TeboSwitch teboSwitch, Object checkedItem, int checkedId) {
        if (checkedId < 0)
            return;

        if (mLastCheckedId == checkedId) {
            return;
        }

        mLastCheckedId = checkedId;

        YesNo result = ((YesNo)checkedItem);

        if (result == YesNo.YES) {
            //record.setShipmentDate(new Date());
            getContentBinding().dtpShipmentDate.setVisibility(View.VISIBLE);
            getContentBinding().txtShipmentDetails.setVisibility(View.VISIBLE);
            //getContentBinding().divShippingStatusTop.setVisibility(View.VISIBLE);
            //getContentBinding().divShippingStatusBottom.setVisibility(View.VISIBLE);
            getContentBinding().sampleReceivedLayout.setVisibility(View.VISIBLE);
        } else {
            getContentBinding().dtpShipmentDate.setVisibility(View.GONE);
            getContentBinding().txtShipmentDetails.setVisibility(View.GONE);
            //getContentBinding().divShippingStatusTop.setVisibility(View.GONE);
            //getContentBinding().divShippingStatusBottom.setVisibility(View.GONE);
            getContentBinding().sampleReceivedLayout.setVisibility(View.GONE);
        }
    }

    public static SampleEditFragment newInstance(IActivityCommunicator activityCommunicator, SampleFormNavigationCapsule capsule, Sample activityRootData) {
        return newInstance(activityCommunicator, SampleEditFragment.class, capsule, activityRootData);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (onResumeTask != null && !onResumeTask.isCancelled())
            onResumeTask.cancel(true);

        if (saveSample != null && !saveSample.isCancelled())
            saveSample.cancel(true);
    }

    @Override
    public void save(final NotificationContext nContext) {
        final Sample sampleToSave = getActivityRootData();

        if (sampleToSave == null)
            throw new IllegalArgumentException("sampleToSave is null");

        if (sampleToSave.getReportingUser() == null) {
            sampleToSave.setReportingUser(ConfigProvider.getUser());
        }
        if (sampleToSave.getReportDateTime() == null) {
            sampleToSave.setReportDateTime(new Date());
        }

        SampleValidator.clearErrorsForSampleData(getContentBinding());
        if (!SampleValidator.validateSampleData(nContext, sampleToSave, getContentBinding())) {
            return;
        }

        try {
            ITaskExecutor executor = TaskExecutorFor.job(new IJobDefinition() {
                private String saveUnsuccessful;

                @Override
                public void preExecute(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    //getActivityCommunicator().showPreloader();
                    //getActivityCommunicator().hideFragmentView();

                    saveUnsuccessful = String.format(getResources().getString(R.string.snackbar_save_error), getResources().getString(R.string.entity_sample));
                }

                @Override
                public void execute(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    try {
                        SampleDao sampleDao = DatabaseHelper.getSampleDao();
                        sampleDao.saveAndSnapshot(sampleToSave);
                    } catch (DaoException e) {
                        Log.e(getClass().getName(), "Error while trying to save sample", e);
                        resultHolder.setResultStatus(new BoolResult(false, saveUnsuccessful));
                        ErrorReportingHelper.sendCaughtException(tracker, e, sampleToSave, true);
                    }
                }
            });
            saveSample = executor.execute(new ITaskResultCallback() {
                @Override
                public void taskResult(BoolResult resultStatus, TaskResultHolder resultHolder) {
                    //getActivityCommunicator().hidePreloader();
                    //getActivityCommunicator().showFragmentView();
                    if (resultHolder == null){
                        return;
                    }

                    if (!resultStatus.isSuccess()) {
                        NotificationHelper.showNotification(nContext, NotificationType.ERROR, resultStatus.getMessage());
                        return;
                    } else {
                        NotificationHelper.showNotification(nContext, NotificationType.SUCCESS, "Sample " + DataHelper.getShortUuid(sampleToSave.getUuid()) + " saved");
                    }

                    if (RetroProvider.isConnected()) {
                        SynchronizeDataAsync.callWithProgressDialog(SynchronizeDataAsync.SyncMode.Changes, getContext(), new SyncCallback() {
                            @Override
                            public void call(boolean syncFailed, String syncFailedMessage) {
                                if (syncFailed) {
                                    NotificationHelper.showNotification(nContext, NotificationType.WARNING, String.format(getResources().getString(R.string.snackbar_sync_error_saved), getResources().getString(R.string.entity_sample)));
                                } else {
                                    NotificationHelper.showNotification(nContext, NotificationType.SUCCESS, String.format(getResources().getString(R.string.snackbar_save_success), getResources().getString(R.string.entity_sample)));
                                }
                                getActivity().finish();
                            }
                        });
                    } else {
                        NotificationHelper.showNotification(nContext, NotificationType.SUCCESS, String.format(getResources().getString(R.string.snackbar_save_success), getResources().getString(R.string.entity_sample)));
                        getActivity().finish();
                    }

                }
            });
        } catch (Exception ex) {
            //getActivityCommunicator().hidePreloader();
            //getActivityCommunicator().showFragmentView();
        }

    }
}