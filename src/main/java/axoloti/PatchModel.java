/**
 * Copyright (C) 2013 - 2016 Johannes Taelman
 *
 * This file is part of Axoloti.
 *
 * Axoloti is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Axoloti is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Axoloti. If not, see <http://www.gnu.org/licenses/>.
 */
package axoloti;

import axoloti.attribute.AttributeInstance;
import axoloti.displays.DisplayInstance;
import axoloti.inlets.InletInstance;
import axoloti.object.AxoObjectAbstract;
import axoloti.object.AxoObjectInstance;
import axoloti.object.AxoObjectInstanceAbstract;
import axoloti.object.AxoObjectInstanceComment;
import axoloti.object.AxoObjectInstanceHyperlink;
import axoloti.object.AxoObjectInstancePatcher;
import axoloti.object.AxoObjectInstancePatcherObject;
import axoloti.object.AxoObjectInstanceZombie;
import axoloti.object.AxoObjectPatcher;
import axoloti.object.AxoObjectPatcherObject;
import axoloti.object.AxoObjectZombie;
import axoloti.object.AxoObjects;
import axoloti.outlets.OutletInstance;
import axoloti.parameters.ParameterInstance;
import axoloti.utils.AxolotiLibrary;
import axoloti.utils.Constants;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.simpleframework.xml.*;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Complete;
import org.simpleframework.xml.core.Persist;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.core.Validate;
import org.simpleframework.xml.strategy.Strategy;



/**
 *
 * @author Johannes Taelman
 */
@Root
public class PatchModel {

    //TODO - use execution order, rather than UI ordering
    static final boolean USE_EXECUTION_ORDER = false;

    @Attribute(required = false)
    String appVersion;
    public @ElementListUnion({
        @ElementList(entry = "obj", type = AxoObjectInstance.class, inline = true, required = false),
        @ElementList(entry = "patcher", type = AxoObjectInstancePatcher.class, inline = true, required = false),
        @ElementList(entry = "patchobj", type = AxoObjectInstancePatcherObject.class, inline = true, required = false),
        @ElementList(entry = "comment", type = AxoObjectInstanceComment.class, inline = true, required = false),
        @ElementList(entry = "hyperlink", type = AxoObjectInstanceHyperlink.class, inline = true, required = false),
        @ElementList(entry = "zombie", type = AxoObjectInstanceZombie.class, inline = true, required = false)})
    ArrayList<AxoObjectInstanceAbstract> objectinstances = new ArrayList<AxoObjectInstanceAbstract>();
    @ElementList(name = "nets")
    public ArrayList<Net> nets = new ArrayList<Net>();
    @Element(required = false)
    PatchSettings settings;
    @Element(required = false, data = true)
    String notes = "";
    @Element(required = false)
    Rectangle windowPos;
    private String FileNamePath;
    ArrayList<ParameterInstance> ParameterInstances = new ArrayList<ParameterInstance>();
    ArrayList<DisplayInstance> DisplayInstances = new ArrayList<DisplayInstance>();
    private ArrayList<Modulator> Modulators = new ArrayList<Modulator>();
    private boolean dirty = false;
    @Element(required = false)
    private String helpPatch;

    private ArrayList<ModelChangedListener> modelChangedListeners = new ArrayList<ModelChangedListener>();

    private boolean loadingUndoState = false;

    // patch this patch is contained in
    private PatchModel container = null;
    
    private AxoObjectInstanceAbstract controllerinstance;
    public AxoObjectInstanceAbstract getControllerObject() { return controllerinstance;}
    public void setControllerObject(AxoObjectInstanceAbstract o) { controllerinstance=o;}
    public String getHelpPatch() { return helpPatch;}

    public boolean presetUpdatePending = false;

    boolean locked = false;

    private List<String> undoStates = new ArrayList<String>();
    public int currentState = 0;

    static public class PatchVersionException
            extends RuntimeException {

        PatchVersionException(String msg) {
            super(msg);
        }
    }

    private static final int AVX = getVersionX(Version.AXOLOTI_SHORT_VERSION),
            AVY = getVersionY(Version.AXOLOTI_SHORT_VERSION),
            AVZ = getVersionZ(Version.AXOLOTI_SHORT_VERSION);

    private static int getVersionX(String vS) {
        if (vS != null) {
            int i = vS.indexOf('.');
            if (i > 0) {
                String v = vS.substring(0, i);
                try {
                    return Integer.valueOf(v);
                } catch (NumberFormatException e) {
                }
            }
        }
        return -1;
    }

    private static int getVersionY(String vS) {
        if (vS != null) {
            int i = vS.indexOf('.');
            if (i > 0) {
                int j = vS.indexOf('.', i + 1);
                if (j > 0) {
                    String v = vS.substring(i + 1, j);
                    try {
                        return Integer.valueOf(v);
                    } catch (NumberFormatException e) {

                    }
                }
            }
        }
        return -1;
    }

    private static int getVersionZ(String vS) {
        if (vS != null) {
            int i = vS.indexOf('.');
            if (i > 0) {
                int j = vS.indexOf('.', i + 1);
                if (j > 0) {
                    String v = vS.substring(j + 1);
                    try {
                        return Integer.valueOf(v);
                    } catch (NumberFormatException e) {

                    }
                }
            }
        }
        return -1;
    }

    @Validate
    public void Validate() {
        // called after deserialializtion, stops validation
        if (appVersion != null
                && !appVersion.equals(Version.AXOLOTI_SHORT_VERSION)) {
            int vX = getVersionX(appVersion);
            int vY = getVersionY(appVersion);
            int vZ = getVersionZ(appVersion);

            if (AVX > vX) {
                return;
            }
            if (AVX == vX) {
                if (AVY > vY) {
                    return;
                }
                if (AVY == vY) {
                    if (AVZ > vZ) {
                        return;
                    }
                    if (AVZ == vZ) {
                        return;
                    }
                }
            }

            throw new PatchVersionException(appVersion);
        }
    }

    @Complete
    public void Complete() {
        // called after deserialializtion
    }

    @Persist
    public void Persist() {
        // called prior to serialization
        appVersion = Version.AXOLOTI_SHORT_VERSION;
    }

    public PatchSettings getSettings() {
        return settings;
    }

    public void setFileNamePath(String FileNamePath) {
        this.FileNamePath = FileNamePath;
    }

    public String getFileNamePath() {
        return FileNamePath;
    }

    public PatchModel() {
        super();
    }
    int IID = -1; // iid identifies the patch

    public int GetIID() {
        return IID;
    }

    void CreateIID() {
        java.util.Random r = new java.util.Random();
        IID = r.nextInt();
    }

    public void PostContructor() {
        for (AxoObjectInstanceAbstract o : objectinstances) {
            o.patchModel = this;
            AxoObjectAbstract t = o.resolveType();
            if ((t != null) && (t.providesModulationSource())) {

                Modulator[] m = t.getModulators();
                if (Modulators == null) {
                    Modulators = new ArrayList<Modulator>();
                }
                for (Modulator mm : m) {
                    mm.objinst = o;
                    Modulators.add(mm);
                }
            }
        }

        ArrayList<AxoObjectInstanceAbstract> obj2 = (ArrayList<AxoObjectInstanceAbstract>) objectinstances.clone();
        for (AxoObjectInstanceAbstract o : obj2) {
            AxoObjectAbstract t = o.getType();
            if ((t != null) && (!t.providesModulationSource())) {
                o.patchModel = this;
                //System.out.println("Obj added " + o.getInstanceName());
            } else if (t == null) {
                objectinstances.remove(o);
                AxoObjectInstanceZombie zombie = new AxoObjectInstanceZombie(new AxoObjectZombie(), this, o.getInstanceName(), new Point(o.getX(), o.getY()));
                zombie.patchModel = this;
                zombie.typeName = o.typeName;
                objectinstances.add(zombie);
            }
        }
        ArrayList<Net> nets2 = (ArrayList<Net>) nets.clone();
        for (Net n : nets2) {
            n.patchModel = this;
        }
        if (settings == null) {
            settings = new PatchSettings();
        }
        ClearDirty();
    }

    public ArrayList<ParameterInstance> getParameterInstances() {
        return ParameterInstances;
    }

    public AxoObjectInstanceAbstract GetObjectInstance(String n) {
        for (AxoObjectInstanceAbstract o : objectinstances) {
            if (n.equals(o.getInstanceName())) {
                return o;
            }
        }
        return null;
    }

    public void ClearDirty() {
        dirty = false;
    }

    public void setDirty() {
        notifyModelChangedListeners();
        dirty = true;

        if (container != null) {
            container.setDirty();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public PatchModel container() {
        return container;
    }

    public void setContainer(PatchModel c) {
        container = c;
    }

    public AxoObjectInstanceAbstract AddObjectInstance(AxoObjectAbstract obj, Point loc) {
        if (obj == null) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "AddObjectInstance NULL");
            return null;
        }
        int i = 1;
        String n = obj.getDefaultInstanceName() + "_";
        while (GetObjectInstance(n + i) != null) {
            i++;
        }
        AxoObjectInstanceAbstract objinst = obj.CreateInstance(this, n + i, loc);
        setDirty();

        Modulator[] m = obj.getModulators();
        if (m != null) {
            if (Modulators == null) {
                Modulators = new ArrayList<Modulator>();
            }
            for (Modulator mm : m) {
                mm.objinst = objinst;
                Modulators.add(mm);
            }
        }

        return objinst;
    }

    public Net GetNet(InletInstance io) {
        for (Net net : nets) {
            for (InletInstance d : net.dest) {
                if (d == io) {
                    return net;
                }
            }
        }
        return null;
    }

    public Net GetNet(OutletInstance io) {
        for (Net net : nets) {
            for (OutletInstance d : net.source) {
                if (d == io) {
                    return net;
                }
            }
        }
        return null;
    }

    /*
     private boolean CompatType(DataType source, DataType d2){
     if (d1 == d2) return true;
     if ((d1 == DataType.bool32)&&(d2 == DataType.frac32)) return true;
     if ((d1 == DataType.frac32)&&(d2 == DataType.bool32)) return true;
     return false;
     }*/
    public Net AddConnection(InletInstance il, OutletInstance ol) {
        if (il.getObjectInstance().getPatchModel() != this) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        if (ol.getObjectInstance().getPatchModel() != this) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        Net n1, n2;
        n1 = GetNet(il);
        n2 = GetNet(ol);
        if ((n1 == null) && (n2 == null)) {
            Net n = new Net(this);
            nets.add(n);
            n.connectInlet(il);
            n.connectOutlet(ol);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: new net added");
            return n;
        } else if (n1 == n2) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: already connected");
            return null;
        } else if ((n1 != null) && (n2 == null)) {
            if (n1.source.isEmpty()) {
                Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: adding outlet to inlet net");
                n1.connectOutlet(ol);
                return n1;
            } else {
                disconnect(il);
                Net n = new Net(this);
                nets.add(n);
                n.connectInlet(il);
                n.connectOutlet(ol);
                Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: new net added");
                return n;
            }
        } else if ((n1 == null) && (n2 != null)) {
            n2.connectInlet(il);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: add additional outlet");
            return n2;
        } else if ((n1 != null) && (n2 != null)) {
            // inlet already has connect, and outlet has another
            // replace
            disconnect(il);
            n2.connectInlet(il);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: replace inlet with existing net");
            return n2;
        }
        return null;
    }

    public Net AddConnection(InletInstance il, InletInstance ol) {
        if (il == ol) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: same inlet");
            return null;
        }
        if (il.getObjectInstance().patchModel != this) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        if (ol.getObjectInstance().patchModel != this) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: different patch");
            return null;
        }
        Net n1, n2;
        n1 = GetNet(il);
        n2 = GetNet(ol);
        if ((n1 == null) && (n2 == null)) {
            Net n = new Net(this);
            nets.add(n);
            n.connectInlet(il);
            n.connectInlet(ol);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: new net added");
            return n;
        } else if (n1 == n2) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: already connected");
        } else if ((n1 != null) && (n2 == null)) {
            n1.connectInlet(ol);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: inlet added");
            return n1;
        } else if ((n1 == null) && (n2 != null)) {
            n2.connectInlet(il);
            Logger.getLogger(PatchModel.class.getName()).log(Level.FINE, "connect: inlet added");
            return n2;
        } else if ((n1 != null) && (n2 != null)) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.INFO, "can't connect: both inlets included in net");
            return null;
        }
        return null;
    }

    public Net disconnect(InletInstance io) {
        Net n = GetNet(io);
        if (n != null) {
            n.dest.remove(io);
            if (n.source.size() + n.dest.size() <= 1) {
                delete(n);
            }
            return n;
        }

        return null;
    }

    public Net disconnect(OutletInstance io) {
        Net n = GetNet(io);
        if (n != null) {
            n.source.remove((OutletInstance) io);
            if (n.source.size() + n.dest.size() <= 1) {
                delete(n);
            }
            return n;
        }
        return null;
    }

    public Net delete(Net n) {
        nets.remove(n);
        return n;
    }

    public boolean delete(AxoObjectInstanceAbstract o) {
        boolean deletionSucceeded = false;
        if (o == null) {
            return deletionSucceeded;
        }
        for (InletInstance ii : o.getInletInstances()) {
            disconnect(ii);
        }
        for (OutletInstance oi : o.getOutletInstances()) {
            disconnect(oi);
        }
        int i;
        for (i = Modulators.size() - 1; i >= 0; i--) {
            Modulator m1 = Modulators.get(i);
            if (m1.objinst == o) {
                Modulators.remove(m1);
                for (Modulation mt : m1.Modulations) {
                    mt.destination.removeModulation(mt);
                }
            }
        }
        deletionSucceeded = objectinstances.remove(o);
        AxoObjectAbstract t = o.getType();
        if (o != null) {
            //            o.Close();
            t.DeleteInstance(o);
        }
        return deletionSucceeded;
    }

    public void updateModulation(Modulation n) {
        // find modulator
        Modulator m = null;
        for (Modulator m1 : Modulators) {
            if (m1.objinst == n.source) {
                if ((m1.name == null) || (m1.name.isEmpty())) {
                    m = m1;
                    break;
                } else if (m1.name.equals(n.modName)) {
                    m = m1;
                    break;
                }
            }
        }
        if (m == null) {
            throw new UnsupportedOperationException("Modulator not found");
        }
        if (!m.Modulations.contains(n)) {
            m.Modulations.add(n);
            System.out.println("modulation added to Modulator " + Modulators.indexOf(m));
        }
    }

    public void pushUndoState() {
        pushUndoState(true);
    }

    public void pushUndoState(boolean advanceCurrentState) {
        if (advanceCurrentState) {
            currentState += 1;
        }
        saveState();
        cleanUpDanglingUndoStates();
    }

    public void popUndoState() {
        currentState -= 1;
    }

    public void cleanUpDanglingUndoStates() {
        try {
            undoStates.subList(currentState + 1, undoStates.size()).clear();
        } catch (IndexOutOfBoundsException e) {
            // ignore
        }
    }

    void saveState() {
        SortByPosition();
        Serializer serializer = new Persister();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try {
            serializer.write(this, b);
            try {
                undoStates.set(currentState, b.toString());
            } catch (IndexOutOfBoundsException e) {
                undoStates.add(b.toString());
            }
        } catch (Exception ex) {
            Logger.getLogger(AxoObjects.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean isLoadingUndoState() {
        return loadingUndoState;
    }

    public void setLoadingUndoState(boolean loadingUndoState) {
        this.loadingUndoState = loadingUndoState;
    }

    void loadState() {
        Serializer serializer = new Persister();
        ByteArrayInputStream b = new ByteArrayInputStream(undoStates.get(currentState).getBytes());
        try {
            PatchModel p = serializer.read(PatchModel.class, b);
            objectinstances = p.objectinstances;
            for (AxoObjectInstanceAbstract o : objectinstances) {
                o.setPatchModel(this);
            }
            nets = p.nets;
            Modulators = p.Modulators;
            for (Net n : nets) {
                n.setPatchModel(this);
            }
            PostContructor();
            setLoadingUndoState(true);
            notifyModelChangedListeners();
        } catch (Exception ex) {
            Logger.getLogger(AxoObjects.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    boolean save(File f) {
        SortByPosition();
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        try {
            serializer.write(this, f);
            MainFrame.prefs.addRecentFile(f.getAbsolutePath());
            dirty = false;
        } catch (Exception ex) {
            Logger.getLogger(AxoObjects.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
//        if (settings == null) {
//            return;
//        }
//        if (settings.subpatchmode == SubPatchMode.no) {
//            return;
//        }
        /*
         String axoObjPath = getFileNamePath();
         int i = axoObjPath.lastIndexOf(".axp");
         axoObjPath = axoObjPath.substring(0, i) + ".axo";
         Logger.getLogger(Patch.class.getName()).log(Level.INFO, "exporting axo to " + axoObjPath);
         File f2 = new File(axoObjPath);
         ExportAxoObj(f2);
         MainFrame.axoObjects.LoadAxoObjects();
         */
    }

    int displayDataLength = 0;

    void refreshIndexes() {
        for (AxoObjectInstanceAbstract o : objectinstances) {
            o.refreshIndex();
        }
        int i = 0;
        ParameterInstances = new ArrayList<ParameterInstance>();
        for (AxoObjectInstanceAbstract o : objectinstances) {
            for (ParameterInstance p : o.getParameterInstances()) {
                p.setIndex(i);
                i++;
                ParameterInstances.add(p);
            }
        }
        int offset = 0;
        // 0 : header
        // 1 : patchref
        // 2 : length

        DisplayInstances = new ArrayList<DisplayInstance>();
        for (AxoObjectInstanceAbstract o : objectinstances) {
            for (DisplayInstance p : o.getDisplayInstances()) {
                p.setOffset(offset + 3);
                int l = p.getLength();
                offset += l;
                DisplayInstances.add(p);
            }
        }
        displayDataLength = offset;
    }

    void SortByPosition() {
        Collections.sort(this.objectinstances);
        refreshIndexes();
    }

    void SortParentsByExecution(AxoObjectInstanceAbstract o, LinkedList<AxoObjectInstanceAbstract> result) {
        LinkedList<AxoObjectInstanceAbstract> before = new LinkedList<AxoObjectInstanceAbstract>(result);
        LinkedList<AxoObjectInstanceAbstract> parents = new LinkedList<AxoObjectInstanceAbstract>();
        // get the parents
        for (InletInstance il : o.getInletInstances()) {
            Net n = GetNet(il);
            if (n != null) {
                for (OutletInstance ol : n.GetSource()) {
                    AxoObjectInstanceAbstract i = ol.getObjectInstance();
                    if (!parents.contains(i)) {
                        parents.add(i);
                    }
                }
            }
        }
        // sort the parents
        Collections.sort(parents);
        // prepend any we haven't seen before
        for (AxoObjectInstanceAbstract c : parents) {
            if (result.contains(c)) {
                result.remove(c);
            }
            result.addFirst(c);
        }
        // prepend their parents
        for (AxoObjectInstanceAbstract c : parents) {
            if (!before.contains(c)) {
                SortParentsByExecution(c, result);
            }
        }
    }

    void SortByExecution() {
        LinkedList<AxoObjectInstanceAbstract> endpoints = new LinkedList<AxoObjectInstanceAbstract>();
        LinkedList<AxoObjectInstanceAbstract> result = new LinkedList<AxoObjectInstanceAbstract>();
        // start with all objects without outlets (end points)
        for (AxoObjectInstanceAbstract o : objectinstances) {
            if (o.getOutletInstances().isEmpty()) {
                endpoints.add(o);
            } else {
                int count = 0;
                for (OutletInstance ol : o.getOutletInstances()) {
                    if (GetNet(ol) != null) {
                        count++;
                    }
                }
                if (count == 0) {
                    endpoints.add(o);
                }
            }
        }
        // sort them by position
        Collections.sort(endpoints);
        // walk their inlets
        for (AxoObjectInstanceAbstract o : endpoints) {
            SortParentsByExecution(o, result);
        }
        // add the end points
        result.addAll(endpoints);
        // turn it back into a freshly sorted array
        objectinstances = new ArrayList<AxoObjectInstanceAbstract>(result);
        refreshIndexes();
    }

    public Modulator GetModulatorOfModulation(Modulation modulation) {
        if (Modulators == null) {
            return null;
        }
        for (Modulator m : Modulators) {
            if (m.Modulations.contains(modulation)) {
                return m;
            }
        }
        return null;
    }

    public int GetModulatorIndexOfModulation(Modulation modulation) {
        if (Modulators == null) {
            return -1;
        }
        for (Modulator m : Modulators) {
            int i = m.Modulations.indexOf(modulation);
            if (i >= 0) {
                return i;
            }
        }
        return -1;
    }

    List<AxoObjectAbstract> GetUsedAxoObjects() {
        ArrayList<AxoObjectAbstract> aos = new ArrayList<AxoObjectAbstract>();
        for (AxoObjectInstanceAbstract o : objectinstances) {
            if (!aos.contains(o.getType())) {
                aos.add(o.getType());
            }
        }
        return aos;
    }

    public HashSet<String> getIncludes() {
        HashSet<String> includes = new HashSet<String>();
        if (controllerinstance != null) {
            Set<String> i = controllerinstance.getType().GetIncludes();
            if (i != null) {
                includes.addAll(i);
            }
        }
        for (AxoObjectInstanceAbstract o : objectinstances) {
            Set<String> i = o.getType().GetIncludes();
            if (i != null) {
                includes.addAll(i);
            }
        }

        return includes;
    }

    public HashSet<String> getDepends() {
        HashSet<String> depends = new HashSet<String>();
        for (AxoObjectInstanceAbstract o : objectinstances) {
            Set<String> i = o.getType().GetDepends();
            if (i != null) {
                depends.addAll(i);
            }
        }
        return depends;
    }

    public HashSet<String> getModules() {
        HashSet<String> modules = new HashSet<>();
        for (AxoObjectInstanceAbstract o : objectinstances) {
            Set<String> i = o.getType().GetModules();
            if (i != null) {
                modules.addAll(i);
            }
        }
        return modules;
    }

    public String getModuleDir(String module){
        for (AxolotiLibrary lib : MainFrame.prefs.getLibraries()) {
            File f = new File(lib.getLocalLocation() + "modules/" + module);
            if(f.exists() && f.isDirectory()) {
                return lib.getLocalLocation() + "modules/" +module;
            }
        }
        return null;
    }

    void ClearCurrentPreset() {
    }

    void CopyCurrentToInit() {
    }

    void DifferenceToPreset() {
    }

    public int[] DistillPreset(int i) {
        int[] pdata;
        pdata = new int[settings.GetNPresetEntries() * 2];
        for (int j = 0; j < settings.GetNPresetEntries(); j++) {
            pdata[j * 2] = -1;
        }
        int index = 0;
        for (AxoObjectInstanceAbstract o : objectinstances) {
            for (ParameterInstance param : o.getParameterInstances()) {
                ParameterInstance p7 = (ParameterInstance) param;
                Preset p = p7.GetPreset(i);
                if (p != null) {
                    pdata[index * 2] = p7.getIndex();
                    pdata[index * 2 + 1] = p.value.getRaw();
                    index++;
                    if (index == settings.GetNPresetEntries()) {
                        Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "more than {0}entries in preset, skipping...", settings.GetNPresetEntries());
                        return pdata;
                    }
                }
            }
        }
        return pdata;
    }

    public void transferObjectConnections(AxoObjectInstanceZombie oldObject, AxoObjectInstance newObject) {
        transferObjectConnections(oldObject.getInletInstances(),
                oldObject.getOutletInstances(),
                newObject);
    }

    public void transferObjectConnections(AxoObjectInstance oldObject, AxoObjectInstance newObject) {
        transferObjectConnections(oldObject.inletInstances,
                oldObject.outletInstances,
                newObject);
    }

    public void transferObjectConnections(ArrayList<InletInstance> inletInstances,
            ArrayList<OutletInstance> outletInstances,
            AxoObjectInstance newObject) {
        for (int i = 0; i < outletInstances.size(); i++) {
            OutletInstance oldOutletInstance = outletInstances.get(i);
            try {
                OutletInstance newOutletInstance = newObject.outletInstances.get(i);
                Net net = GetNet(oldOutletInstance);

                if (net != null) {
                    ArrayList<InletInstance> dest = (ArrayList< InletInstance>) net.dest.clone();
                    disconnect(oldOutletInstance);
                    for (InletInstance ii : dest) {
                        AddConnection(ii, newOutletInstance);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }

        for (int i = 0; i < inletInstances.size(); i++) {
            InletInstance oldInletInstance = inletInstances.get(i);
            try {
                InletInstance newInletInstance = newObject.inletInstances.get(i);
                Net net = GetNet(oldInletInstance);
                if (net != null) {
                    ArrayList<OutletInstance> source = (ArrayList< OutletInstance>) net.source.clone();
                    disconnect(oldInletInstance);
                    for (OutletInstance oi : source) {
                        AddConnection(newInletInstance, oi);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
    }

    public void transferInputValues(AxoObjectInstance oldObject, AxoObjectInstance newObject) {
        for (int i = 0; i < oldObject.getParameterInstances().size(); i++) {
            ParameterInstance oldParameterInstance = oldObject.getParameterInstances().get(i);
            try {
                ParameterInstance newParameterInstance = newObject.getParameterInstances().get(i);
                newParameterInstance.CopyValueFrom(oldParameterInstance);
                newObject.setDirty(true);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
        for (int i = 0; i < oldObject.getAttributeInstances().size(); i++) {
            AttributeInstance oldAttributeInstance = oldObject.getAttributeInstances().get(i);
            try {
                AttributeInstance newAttributeInstance = newObject.getAttributeInstances().get(i);
                newAttributeInstance.CopyValueFrom(oldAttributeInstance);
                newObject.setDirty(true);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
    }

    public void transferState(AxoObjectInstance oldObject, AxoObjectInstance newObject) {
        transferObjectConnections(oldObject, newObject);
        transferInputValues(oldObject, newObject);
    }

    public AxoObjectInstanceAbstract ChangeObjectInstanceType1(AxoObjectInstanceAbstract oldObject, AxoObjectAbstract newObjectType) {
        if ((oldObject instanceof AxoObjectInstancePatcher) && (newObjectType instanceof AxoObjectPatcher)) {
            return oldObject;
        } else if ((oldObject instanceof AxoObjectInstancePatcherObject) && (newObjectType instanceof AxoObjectPatcherObject)) {
            return oldObject;
        } else if (oldObject instanceof AxoObjectInstance) {
            String n = oldObject.getInstanceName();
            oldObject.setInstanceName(n + Constants.TEMP_OBJECT_SUFFIX);
            AxoObjectInstanceAbstract newObject = AddObjectInstance(newObjectType, new Point(oldObject.getX(), oldObject.getY()));

            if (newObject instanceof AxoObjectInstance) {
                transferState((AxoObjectInstance) oldObject, (AxoObjectInstance) newObject);
            }
            return newObject;
        } else if (oldObject instanceof AxoObjectInstanceZombie) {
            AxoObjectInstanceAbstract newObject = AddObjectInstance(newObjectType, new Point(oldObject.getX(), oldObject.getY()));
            if ((newObject instanceof AxoObjectInstance)) {
                transferObjectConnections((AxoObjectInstanceZombie) oldObject, (AxoObjectInstance) newObject);
            }
            return newObject;
        }
        return oldObject;
    }

    public AxoObjectInstanceAbstract ChangeObjectInstanceType(AxoObjectInstanceAbstract oldObject, AxoObjectAbstract newObjectType) {
        AxoObjectInstanceAbstract newObject = ChangeObjectInstanceType1(oldObject, newObjectType);
        if (newObject != oldObject) {
            delete(oldObject);
            setDirty();
        }
        return newObject;
    }

    /**
     *
     * @param initial If true, only objects restored from object name reference
     * (not UUID) will promote to a variant with the same name.
     */
    public boolean PromoteOverloading(boolean initial) {
        refreshIndexes();
        Set<String> ProcessedInstances = new HashSet<String>();
        boolean p = true;
        boolean promotionOccured = false;
        while (p && !(ProcessedInstances.size() == objectinstances.size())) {
            p = false;
            for (AxoObjectInstanceAbstract o : objectinstances) {
                if (!ProcessedInstances.contains(o.getInstanceName())) {
                    ProcessedInstances.add(o.getInstanceName());
                    if (!initial || o.isTypeWasAmbiguous()) {
                        promotionOccured |= o.PromoteToOverloadedObj();
                    }
                    p = true;
                    break;
                }
            }
        }
        if (!(ProcessedInstances.size() == objectinstances.size())) {
            for (AxoObjectInstanceAbstract o : objectinstances) {
                if (!ProcessedInstances.contains(o.getInstanceName())) {
                    Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, "PromoteOverloading : fault in {0}", o.getInstanceName());
                }
            }
        }
        return promotionOccured;
    }

    public InletInstance getInletByReference(String objname, String inletname) {
        if (objname == null) {
            return null;
        }
        if (inletname == null) {
            return null;
        }
        AxoObjectInstanceAbstract o = GetObjectInstance(objname);
        if (o == null) {
            return null;
        }
        return o.GetInletInstance(inletname);
    }

    public OutletInstance getOutletByReference(String objname, String outletname) {
        if (objname == null) {
            return null;
        }
        if (outletname == null) {
            return null;
        }
        AxoObjectInstanceAbstract o = GetObjectInstance(objname);
        if (o == null) {
            return null;
        }
        return o.GetOutletInstance(outletname);
    }

    public String GetCurrentWorkingDirectory() {
        if (FileNamePath == null) {
            return null;
        }
        int i = FileNamePath.lastIndexOf(File.separatorChar);
        if (i < 0) {
            return null;
        }
        return FileNamePath.substring(0, i);
    }

    public Rectangle getWindowPos() {
        return windowPos;
    }

    public String getNotes() {
        return notes;
    }

    public ArrayList<SDFileReference> GetDependendSDFiles() {
        ArrayList<SDFileReference> files = new ArrayList<SDFileReference>();
        for (AxoObjectInstanceAbstract o : objectinstances) {
            ArrayList<SDFileReference> f2 = o.GetDependendSDFiles();
            if (f2 != null) {
                files.addAll(f2);
            }
        }
        return files;
    }


    public boolean canUndo() {
        return currentState > 0;
    }

    public boolean canRedo() {
        return currentState < undoStates.size() - 1;
    }

    public void undo() {
        if (canUndo()) {
            currentState -= 1;
            loadState();
            setDirty();
        }
    }

    public void redo() {
        if (canRedo()) {
            currentState += 1;
            loadState();
            setDirty();
        }
    }

    public ArrayList<AxoObjectInstanceAbstract> getObjectInstances() {
        return objectinstances;
    }

    public ArrayList<Modulator> getModulators() {
        return Modulators;
    }

    public void addModulator(Modulator m) {
        if (Modulators == null) {
            Modulators = new ArrayList<Modulator>();
        }
        Modulators.add(m);
    }

    public void addObjectInstance(AxoObjectInstanceAbstract o) {
        objectinstances.add(o);
    }

    public ArrayList<Net> getNets() {
        return nets;
    }

    public void addNet(Net n) {
        nets.add(n);
    }

    public void addModelChangedListener(ModelChangedListener listener) {
        modelChangedListeners.add(listener);
    }

    public void notifyModelChangedListeners() {
        for (ModelChangedListener m : modelChangedListeners) {
            m.modelChanged();
        }
    }

    void paste(String v, Point pos, boolean restoreConnectionsToExternalOutlets) {
        if (v.isEmpty()) {
            return;
        }
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        try {
            PatchModel p = serializer.read(PatchModel.class, v);
            Map<String, String> dict = new HashMap<String, String>();
            ArrayList<AxoObjectInstanceAbstract> obj2 = (ArrayList<AxoObjectInstanceAbstract>) p.objectinstances.clone();
            for (AxoObjectInstanceAbstract o : obj2) {
                o.patchModel = this;
                AxoObjectAbstract obj = o.resolveType();
                if (obj != null) {
                    Modulator[] m = obj.getModulators();
                    if (m != null) {
                        if (Modulators == null) {
                            Modulators = new ArrayList<Modulator>();
                        }
                        for (Modulator mm : m) {
                            mm.objinst = o;
                            Modulators.add(mm);
                        }
                    }
                } else {
                    //o.patch = this;
                    p.objectinstances.remove(o);
                    AxoObjectInstanceZombie zombie = new AxoObjectInstanceZombie(new AxoObjectZombie(), this, o.getInstanceName(), new Point(o.getX(), o.getY()));
                    zombie.patchModel = this;
                    zombie.typeName = o.typeName;
                    p.objectinstances.add(zombie);
                }
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (AxoObjectInstanceAbstract o : p.objectinstances) {
                String original_name = o.getInstanceName();
                if (original_name != null) {
                    String new_name = original_name;
                    String ss[] = new_name.split("_");
                    boolean hasNumeralSuffix = false;
                    try {
                        if ((ss.length > 1) && (Integer.toString(Integer.parseInt(ss[ss.length - 1]))).equals(ss[ss.length - 1])) {
                            hasNumeralSuffix = true;
                        }
                    } catch (NumberFormatException e) {
                    }
                    if (hasNumeralSuffix) {
                        int n = Integer.parseInt(ss[ss.length - 1]) + 1;
                        String bs = original_name.substring(0, original_name.length() - ss[ss.length - 1].length());
                        while (GetObjectInstance(new_name) != null) {
                            new_name = bs + n++;
                        }
                        while (dict.containsKey(new_name)) {
                            new_name = bs + n++;
                        }
                    } else {
                        while (GetObjectInstance(new_name) != null) {
                            new_name = new_name + "_";
                        }
                        while (dict.containsKey(new_name)) {
                            new_name = new_name + "_";
                        }
                    }
                    if (!new_name.equals(original_name)) {
                        o.setInstanceName(new_name);
                    }
                    dict.put(original_name, new_name);
                }
                if (o.getX() < minX) {
                    minX = o.getX();
                }
                if (o.getY() < minY) {
                    minY = o.getY();
                }
                o.patchModel = this;
                objectinstances.add(o);
                int newposx = o.getX();
                int newposy = o.getY();

                if (pos != null) {
                    // paste at cursor position, with delta snapped to grid
                    newposx += Constants.X_GRID * ((pos.x - minX + Constants.X_GRID / 2) / Constants.X_GRID);
                    newposy += Constants.Y_GRID * ((pos.y - minY + Constants.Y_GRID / 2) / Constants.Y_GRID);
                }
                while (getObjectAtLocation(newposx, newposy) != null) {
                    newposx += Constants.X_GRID;
                    newposy += Constants.Y_GRID;
                }
                o.setLocation(newposx, newposy);
            }
            for (Net n : p.nets) {
                InletInstance connectedInlet = null;
                OutletInstance connectedOutlet = null;
                if (n.source != null) {
                    ArrayList<OutletInstance> source2 = new ArrayList<OutletInstance>();
                    for (OutletInstance o : n.source) {
                        String objname = o.getObjname();
                        String outletname = o.getOutletname();
                        if ((objname != null) && (outletname != null)) {
                            String on2 = dict.get(objname);
                            if (on2 != null) {
//                                o.name = on2 + " " + r[1];
                                OutletInstance i = new OutletInstance();
                                i.outletname = outletname;
                                i.objname = on2;
                                source2.add(i);
                            } else if (restoreConnectionsToExternalOutlets) {
                                AxoObjectInstanceAbstract obj = GetObjectInstance(objname);
                                if ((obj != null) && (connectedOutlet == null)) {
                                    OutletInstance oi = obj.GetOutletInstance(outletname);
                                    if (oi != null) {
                                        connectedOutlet = oi;
                                    }
                                }
                            }
                        }
                    }
                    n.source = source2;
                }
                if (n.dest != null) {
                    ArrayList<InletInstance> dest2 = new ArrayList<InletInstance>();
                    for (InletInstance o : n.dest) {
                        String objname = o.getObjname();
                        String inletname = o.getInletname();
                        if ((objname != null) && (inletname != null)) {
                            String on2 = dict.get(objname);
                            if (on2 != null) {
                                InletInstance i = new InletInstance();
                                i.inletname = inletname;
                                i.objname = on2;
                                dest2.add(i);
                            }
                        }
                    }
                    n.dest = dest2;
                }
                if (n.source.size() + n.dest.size() > 1) {
                    if ((connectedInlet == null) && (connectedOutlet == null)) {
                        n.patchModel = this;
                        nets.add(n);
                    } else if (connectedInlet != null) {
                        for (InletInstance o : n.dest) {
                            InletInstance o2 = getInletByReference(o.getObjname(), o.getInletname());
                            if ((o2 != null) && (o2 != connectedInlet)) {
                                AddConnection(connectedInlet, o2);
                            }
                        }
                        for (OutletInstance o : n.source) {
                            OutletInstance o2 = getOutletByReference(o.getObjname(), o.getOutletname());
                            if (o2 != null) {
                                AddConnection(connectedInlet, o2);
                            }
                        }
                    } else if (connectedOutlet != null) {
                        for (InletInstance o : n.dest) {
                            InletInstance o2 = getInletByReference(o.getObjname(), o.getInletname());
                            if (o2 != null) {
                                AddConnection(o2, connectedOutlet);
                            }
                        }
                    }
                }
            }
            setDirty();
        } catch (javax.xml.stream.XMLStreamException ex) {
            // silence
        } catch (Exception ex) {
            Logger.getLogger(PatchModel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    AxoObjectInstanceAbstract getObjectAtLocation(int x, int y) {
        for (AxoObjectInstanceAbstract o : getObjectInstances()) {
            if ((o.getX() == x) && (o.getY() == y)) {
                return o;
            }
        }
        return null;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }
}
