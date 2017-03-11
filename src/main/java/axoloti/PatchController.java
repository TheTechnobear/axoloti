package axoloti;

import axoloti.inlets.IInletInstanceView;
import axoloti.object.AxoObjectAbstract;
import axoloti.object.AxoObjectInstanceAbstract;
import axoloti.objectviews.IAxoObjectInstanceView;
import axoloti.outlets.IOutletInstanceView;
import axoloti.parameters.ParameterInstance;
import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import qcmds.QCmdChangeWorkingDirectory;
import qcmds.QCmdCompileModule;
import qcmds.QCmdCompilePatch;
import qcmds.QCmdCreateDirectory;
import qcmds.QCmdLock;
import qcmds.QCmdProcessor;
import qcmds.QCmdRecallPreset;
import qcmds.QCmdStart;
import qcmds.QCmdStop;
import qcmds.QCmdUploadPatch;

public class PatchController {

    public PatchModel patchModel;
    public PatchView  patchView;
    public PatchFrame patchFrame;
    public Platform   platform;

    public PatchController() {
        platform = new PlatformAxoloti();
    }

    public void setPatchModel(PatchModel patchModel) {
        this.patchModel = patchModel;
    }

    public void setPatchView(PatchView patchView) {
        this.patchView = patchView;
    }

    public void setPatchFrame(PatchFrame patchFrame) {
        this.patchFrame = patchFrame;
    }

    QCmdProcessor GetQCmdProcessor() {
        if (patchFrame == null) {
            return null;
        }
        return patchFrame.qcmdprocessor;
    }

    public PatchFrame getPatchFrame() {
        return patchFrame;
    }

    public boolean canUndo() {
        return !isLocked() && patchModel.canUndo();
    }

    public boolean canRedo() {
        return !isLocked() && patchModel.canRedo();
    }

    public void RecallPreset(int i) {
        GetQCmdProcessor().AppendToQueue(new QCmdRecallPreset(i));
    }

    public void ShowPreset(int i) {
        patchView.ShowPreset(i);
    }
    
    public void GoLive() {
        if (patchView != null) {
            patchView.Unlock();
        }

        QCmdProcessor qCmdProcessor = GetQCmdProcessor();

        ShowPreset(0);
        setPresetUpdatePending(false);
        for (AxoObjectInstanceAbstract o : patchModel.getObjectInstances()) {
            for (ParameterInstance pi : o.getParameterInstances()) {
                pi.ClearNeedsTransmit();
            }
        }

        // TODO this should be on platform
        qCmdProcessor.AppendToQueue(new QCmdStop());
        if (USBBulkConnection.GetConnection().GetSDCardPresent()) {

            String f = "/" + getSDCardPath();
            //System.out.println("pathf" + f);
            if (SDCardInfo.getInstance().find(f) == null) {
                qCmdProcessor.AppendToQueue(new QCmdCreateDirectory(f));
            }
            qCmdProcessor.AppendToQueue(new QCmdChangeWorkingDirectory(f));
            UploadDependentFiles(f);
        } else {
            // issue warning when there are dependent files
            ArrayList<SDFileReference> files = patchModel.GetDependendSDFiles();
            if (files.size() > 0) {
                Logger.getLogger(PatchView.class.getName()).log(Level.SEVERE, "Patch requires file {0} on SDCard, but no SDCard mounted", files.get(0).targetPath);
            }
        }
        platform.GoLive(patchModel,qCmdProcessor, this);
    }
    
    public void WriteCode() {
        platform.WriteCode(patchModel);
    }

    public File getBinFile() {
        return platform.getBinFile();
    }    
    
    public void Compile() {
        platform.Compile(patchModel,GetQCmdProcessor(),this);
    }

    void UploadDependentFiles(String sdpath) {
        platform.UploadDependentFiles(patchModel,GetQCmdProcessor(),sdpath);
    }

    public void UploadToSDCard(String sdfilename) {
        platform.UploadToSDCard(patchModel, sdfilename,QCmdProcessor.getQCmdProcessor(), this);
    }

    public void UploadToSDCard() {
        UploadToSDCard("/" + getSDCardPath() + "/patch.bin");
    }

    private void pushUndoState(boolean changeOccurred) {
        if (changeOccurred) {
            pushUndoState();
        }
    }

    private void finalizeModelChange(boolean changeOccurred) {
        if (changeOccurred) {
            pushUndoState();
            setDirty();
        }
    }

    public Net disconnect(IInletInstanceView ii) {
        if (!isLocked()) {
            Net net = ii.getInletInstance().disconnect();
            finalizeModelChange(net != null);
            return net;
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "Can't disconnect: locked!");
            return null;
        }
    }

    public Net disconnect(IOutletInstanceView oi) {
        if (!isLocked()) {
            Net net = oi.getOutletInstance().disconnect();
            finalizeModelChange(net != null);
            return net;
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "Can't disconnect: locked!");
            return null;
        }
    }

    public Net AddConnection(IInletInstanceView il, IOutletInstanceView ol) {
        if (!isLocked()) {
            Net net = patchModel.AddConnection(il.getInletInstance(), ol.getOutletInstance());
            pushUndoState(net != null);
            return net;
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "can't add connection: locked");
            return null;
        }
    }

    public Net AddConnection(IInletInstanceView il, IInletInstanceView ol) {
        if (!isLocked()) {
            Net net = patchModel.AddConnection(il.getInletInstance(), ol.getInletInstance());
            pushUndoState(net != null);
            return net;
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "Can't add connection: locked!");
            return null;
        }
    }

    public void deleteNet(IInletInstanceView ii) {
        if (!isLocked()) {
            Net net = ii.getInletInstance().deleteNet();
            finalizeModelChange(net != null);
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "Can't delete: locked!");
        }
    }

    public void deleteNet(IOutletInstanceView oi) {
        if (!isLocked()) {
            Net net = oi.getOutletInstance().deleteNet();
            finalizeModelChange(net != null);
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "Can't delete: locked!");
        }
    }

    public void setFileNamePath(String FileNamePath) {
        patchModel.setFileNamePath(FileNamePath);
        if (getPatchFrame() != null) {
            getPatchFrame().setTitle(FileNamePath);
        }
    }

    public boolean delete(IAxoObjectInstanceView o) {
        boolean succeeded = patchModel.delete((AxoObjectInstanceAbstract) o.getModel());
        o.getModel().Close();
        return succeeded;
    }

    public AxoObjectInstanceAbstract AddObjectInstance(AxoObjectAbstract obj, Point loc) {
        if (!isLocked()) {
            AxoObjectInstanceAbstract object = patchModel.AddObjectInstance(obj, loc);
            pushUndoState(object != null);
            return object;
        } else {
            Logger.getLogger(PatchController.class.getName()).log(Level.INFO, "can't add connection: locked!");
            return null;
        }
    }

    public String GetCurrentWorkingDirectory() {
        return patchModel.GetCurrentWorkingDirectory();
    }

    public void setDirty() {
        patchModel.setDirty();
    }

    public String getFileNamePath() {
        return patchModel.getFileNamePath();
    }

    public String getSDCardPath() {
        String FileNameNoPath = getFileNamePath();
        String separator = System.getProperty("file.separator");
        int lastSeparatorIndex = FileNameNoPath.lastIndexOf(separator);
        if (lastSeparatorIndex > 0) {
            FileNameNoPath = FileNameNoPath.substring(lastSeparatorIndex + 1);
        }
        String FileNameNoExt = FileNameNoPath;
        if (FileNameNoExt.endsWith(".axp") || FileNameNoExt.endsWith(".axs") || FileNameNoExt.endsWith(".axh")) {
            FileNameNoExt = FileNameNoExt.substring(0, FileNameNoExt.length() - 4);
        }
        return FileNameNoExt;
    }


    public void setPresetUpdatePending(boolean updatePending) {
        patchModel.presetUpdatePending = updatePending;
    }

    public boolean isPresetUpdatePending() {
        return patchModel.presetUpdatePending;
    }

    Dimension GetSize() {
        return patchView.GetSize();
    }

    public PatchSettings getSettings() {
        return patchModel.settings;
    }

    public void ShowCompileFail() {
        patchView.ShowCompileFail();
    }

    void paste(String v, Point pos, boolean restoreConnectionsToExternalOutlets) {
        patchModel.paste(v, pos, restoreConnectionsToExternalOutlets);
        pushUndoState();
    }

    public void undo() {
        patchModel.undo();
        patchFrame.updateUndoRedoEnabled();
    }

    public void redo() {
        patchModel.redo();
        patchFrame.updateUndoRedoEnabled();
    }

    public void repaintPatchView() {
        patchView.repaint();
    }

    public Point getViewLocationOnScreen() {
        return patchView.getLocationOnScreen();
    }

    public PatchView getPatchView() {
        return patchView;
    }

    public AxoObjectInstanceAbstract ChangeObjectInstanceType(AxoObjectInstanceAbstract obj, AxoObjectAbstract objType) {
        AxoObjectInstanceAbstract newObject = patchModel.ChangeObjectInstanceType(obj, objType);
        pushUndoState(newObject != obj);
        return newObject;
    }

    public boolean isLoadingUndoState() {
        return patchModel.isLoadingUndoState();
    }

    public void clearLoadingUndoState() {
        patchModel.setLoadingUndoState(false);
    }

    public boolean isLocked() {
        return patchModel.isLocked();
    }

    public void setLocked(boolean locked) {
        patchModel.setLocked(locked);
    }

    public void pushUndoState() {
        patchModel.pushUndoState();
        patchFrame.updateUndoRedoEnabled();
    }

    public void popUndoState() {
        patchModel.popUndoState();
    }

    public Net getNetDraggingModel() {
        return new Net(patchModel);
    }
}
