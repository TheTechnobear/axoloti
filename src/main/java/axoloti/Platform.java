/**
 * Copyright (C) 2013 - 2017 Johannes Taelman
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

import axoloti.object.AxoObject;
import java.io.File;
import qcmds.QCmdProcessor;

/**
 *
 * @author Mark Harris
 */
public class Platform {

    public void GoLive(PatchModel m,QCmdProcessor queue, PatchController controller) {}
    public void UploadDependentFiles(PatchModel m,QCmdProcessor queue, String sdpath){}
    public void Compile(PatchModel m,QCmdProcessor queue, PatchController controller) {}
    public void UploadToSDCard(PatchModel m,String sdfilename, QCmdProcessor queue, PatchController controller) {}
    public File getBinFile() { return null;}

    //these 'raise' questions
    void WriteCode(PatchModel m) {}
    AxoObject GenerateAxoObj(PatchModel m, AxoObject template) {return null;}
}

