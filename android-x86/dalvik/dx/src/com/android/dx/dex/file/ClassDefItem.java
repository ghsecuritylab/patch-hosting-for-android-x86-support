/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dx.dex.file;

import com.android.dx.rop.annotation.Annotations;
import com.android.dx.rop.annotation.AnnotationsList;
import com.android.dx.rop.code.AccessFlags;
import com.android.dx.rop.cst.Constant;
import com.android.dx.rop.cst.CstArray;
import com.android.dx.rop.cst.CstFieldRef;
import com.android.dx.rop.cst.CstMethodRef;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.cst.CstUtf8;
import com.android.dx.rop.type.StdTypeList;
import com.android.dx.rop.type.TypeList;
import com.android.dx.util.AnnotatedOutput;
import com.android.dx.util.Hex;
import com.android.dx.util.Writers;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * Representation of a Dalvik class, which is basically a set of
 * members (fields or methods) along with a few more pieces of
 * information.
 */
public final class ClassDefItem extends IndexedItem {
    /** size of instances when written out to a file, in bytes */
    public static final int WRITE_SIZE = 32;

    /** non-null; type constant for this class */
    private final CstType thisClass;

    /** access flags */
    private final int accessFlags;

    /**
     * null-ok; superclass or <code>null</code> if this class is a/the
     * root class 
     */
    private final CstType superclass;

    /** null-ok; list of implemented interfaces */
    private TypeListItem interfaces;

    /** null-ok; source file name or <code>null</code> if unknown */
    private final CstUtf8 sourceFile;

    /** non-null; associated class data object */
    private final ClassDataItem classData;

    /**
     * null-ok; item wrapper for the static values, initialized
     * in {@link #addContents}
     */
    private EncodedArrayItem staticValuesItem;

    /** non-null; annotations directory */
    private AnnotationsDirectoryItem annotationsDirectory;

    /**
     * Constructs an instance. Its sets of members and annotations are
     * initially empty.
     * 
     * @param thisClass non-null; type constant for this class
     * @param accessFlags access flags
     * @param superclass null-ok; superclass or <code>null</code> if
     * this class is a/the root class
     * @param interfaces non-null; list of implemented interfaces
     * @param sourceFile null-ok; source file name or
     * <code>null</code> if unknown
     */
    public ClassDefItem(CstType thisClass, int accessFlags,
            CstType superclass, TypeList interfaces, CstUtf8 sourceFile) {
        if (thisClass == null) {
            throw new NullPointerException("thisClass == null");
        }

        /*
         * TODO: Maybe check accessFlags and superclass, at
         * least for easily-checked stuff?
         */

        if (interfaces == null) {
            throw new NullPointerException("interfaces == null");
        }

        this.thisClass = thisClass;
        this.accessFlags = accessFlags;
        this.superclass = superclass;
        this.interfaces = 
            (interfaces.size() == 0) ? null :  new TypeListItem(interfaces);
        this.sourceFile = sourceFile;
        this.classData = new ClassDataItem(thisClass);
        this.staticValuesItem = null;
        this.annotationsDirectory = new AnnotationsDirectoryItem();
    }

    /** {@inheritDoc} */
    @Override
    public ItemType itemType() {
        return ItemType.TYPE_CLASS_DEF_ITEM;
    }

    /** {@inheritDoc} */
    @Override
    public int writeSize() {
        return WRITE_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public void addContents(DexFile file) {
        TypeIdsSection typeIds = file.getTypeIds();
        MixedItemSection byteData = file.getByteData();
        MixedItemSection wordData = file.getWordData();
        MixedItemSection typeLists = file.getTypeLists();
        StringIdsSection stringIds = file.getStringIds();

        typeIds.intern(thisClass);

        if (!classData.isEmpty()) {
            MixedItemSection classDataSection = file.getClassData();
            classDataSection.add(classData);

            CstArray staticValues = classData.getStaticValuesConstant();
            if (staticValues != null) {
                staticValuesItem =
                    byteData.intern(new EncodedArrayItem(staticValues));
            }
        }

        if (superclass != null) {
            typeIds.intern(superclass);
        }

        if (interfaces != null) {
            interfaces = typeLists.intern(interfaces);
        }

        if (sourceFile != null) {
            stringIds.intern(sourceFile);
        }

        if (! annotationsDirectory.isEmpty()) {
            if (annotationsDirectory.isInternable()) {
                annotationsDirectory = wordData.intern(annotationsDirectory);
            } else {
                wordData.add(annotationsDirectory);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(DexFile file, AnnotatedOutput out) {
        boolean annotates = out.annotates();
        TypeIdsSection typeIds = file.getTypeIds();
        int classIdx = typeIds.indexOf(thisClass);
        int superIdx = (superclass == null) ? -1 :
            typeIds.indexOf(superclass);
        int interOff = OffsettedItem.getAbsoluteOffsetOr0(interfaces);
        int annoOff = annotationsDirectory.isEmpty() ? 0 :
            annotationsDirectory.getAbsoluteOffset();
        int sourceFileIdx = (sourceFile == null) ? -1 :
            file.getStringIds().indexOf(sourceFile);
        int dataOff = classData.isEmpty()? 0 : classData.getAbsoluteOffset();
        int staticValuesOff =
            OffsettedItem.getAbsoluteOffsetOr0(staticValuesItem);

        if (annotates) {
            out.annotate(0, indexString() + ' ' + thisClass.toHuman());
            out.annotate(4, "  class_idx:           " + Hex.u4(classIdx));
            out.annotate(4, "  access_flags:        " + 
                         AccessFlags.classString(accessFlags));
            out.annotate(4, "  superclass_idx:      " + Hex.u4(superIdx) +
                         " // " + ((superclass == null) ? "<none>" :
                          superclass.toHuman()));
            out.annotate(4, "  interfaces_off:      " + Hex.u4(interOff));
            if (interOff != 0) {
                TypeList list = interfaces.getList();
                int sz = list.size();
                for (int i = 0; i < sz; i++) {
                    out.annotate(0, "    " + list.getType(i).toHuman());
                }
            }
            out.annotate(4, "  source_file_idx:     " + Hex.u4(sourceFileIdx) +
                         " // " + ((sourceFile == null) ? "<none>" :
                          sourceFile.toHuman()));
            out.annotate(4, "  annotations_off:     " + Hex.u4(annoOff));
            out.annotate(4, "  class_data_off:      " + Hex.u4(dataOff));
            out.annotate(4, "  static_values_off:   " +
                    Hex.u4(staticValuesOff));
        }

        out.writeInt(classIdx);
        out.writeInt(accessFlags);
        out.writeInt(superIdx);
        out.writeInt(interOff);
        out.writeInt(sourceFileIdx);
        out.writeInt(annoOff);
        out.writeInt(dataOff);
        out.writeInt(staticValuesOff);
    }

    /**
     * Gets the constant corresponding to this class.
     * 
     * @return non-null; the constant
     */
    public CstType getThisClass() {
        return thisClass;
    }

    /**
     * Gets the access flags.
     * 
     * @return the access flags
     */
    public int getAccessFlags() {
        return accessFlags;
    }

    /**
     * Gets the superclass.
     * 
     * @return null-ok; the superclass or <code>null</code> if
     * this class is a/the root class
     */
    public CstType getSuperclass() {
        return superclass;
    }

    /**
     * Gets the list of interfaces implemented.
     * 
     * @return non-null; the interfaces list
     */
    public TypeList getInterfaces() {
        if (interfaces == null) {
            return StdTypeList.EMPTY;
        }

        return interfaces.getList();
    }

    /**
     * Gets the source file name.
     * 
     * @return null-ok; the source file name or <code>null</code> if unknown
     */
    public CstUtf8 getSourceFile() {
        return sourceFile;
    }

    /**
     * Adds a static field.
     * 
     * @param field non-null; the field to add
     * @param value null-ok; initial value for the field, if any
     */
    public void addStaticField(EncodedField field, Constant value) {
        classData.addStaticField(field, value);
    }

    /**
     * Adds an instance field.
     * 
     * @param field non-null; the field to add
     */
    public void addInstanceField(EncodedField field) {
        classData.addInstanceField(field);
    }

    /**
     * Adds a direct (<code>static</code> and/or <code>private</code>) method.
     * 
     * @param method non-null; the method to add
     */
    public void addDirectMethod(EncodedMethod method) {
        classData.addDirectMethod(method);
    }

    /**
     * Adds a virtual method.
     * 
     * @param method non-null; the method to add
     */
    public void addVirtualMethod(EncodedMethod method) {
        classData.addVirtualMethod(method);
    }

    /**
     * Gets all the methods in this class. The returned list is not linked
     * in any way to the underlying lists contained in this instance, but
     * the objects contained in the list are shared.
     * 
     * @return non-null; list of all methods
     */
    public ArrayList<EncodedMethod> getMethods() {
        return classData.getMethods();
    }

    /**
     * Sets the direct annotations on this class. These are annotations
     * made on the class, per se, as opposed to on one of its members.
     * It is only valid to call this method at most once per instance.
     * 
     * @param annotations non-null; annotations to set for this class
     */
    public void setClassAnnotations(Annotations annotations) {
        annotationsDirectory.setClassAnnotations(annotations);
    }

    /**
     * Adds a field annotations item to this class.
     * 
     * @param field non-null; field in question
     * @param annotations non-null; associated annotations to add
     */
    public void addFieldAnnotations(CstFieldRef field,
            Annotations annotations) {
        annotationsDirectory.addFieldAnnotations(field, annotations);
    }

    /**
     * Adds a method annotations item to this class.
     * 
     * @param method non-null; method in question
     * @param annotations non-null; associated annotations to add
     */
    public void addMethodAnnotations(CstMethodRef method,
            Annotations annotations) {
        annotationsDirectory.addMethodAnnotations(method, annotations);
    }

    /**
     * Adds a parameter annotations item to this class.
     * 
     * @param method non-null; method in question
     * @param list non-null; associated list of annotation sets to add
     */
    public void addParameterAnnotations(CstMethodRef method,
            AnnotationsList list) {
        annotationsDirectory.addParameterAnnotations(method, list);
    }

    /**
     * Gets the method annotations for a given method, if any. This is
     * meant for use by debugging / dumping code.
     * 
     * @param method non-null; the method
     * @return null-ok; the method annotations, if any
     */
    public Annotations getMethodAnnotations(CstMethodRef method) {
        return annotationsDirectory.getMethodAnnotations(method);
    }

    /**
     * Gets the parameter annotations for a given method, if any. This is
     * meant for use by debugging / dumping code.
     * 
     * @param method non-null; the method
     * @return null-ok; the parameter annotations, if any
     */
    public AnnotationsList getParameterAnnotations(CstMethodRef method) {
        return annotationsDirectory.getParameterAnnotations(method);
    }
    
    /**
     * Prints out the contents of this instance, in a debugging-friendly
     * way.
     * 
     * @param out non-null; where to output to
     * @param verbose whether to be verbose with the output
     */
    public void debugPrint(Writer out, boolean verbose) {
        PrintWriter pw = Writers.printWriterFor(out);

        pw.println(getClass().getName() + " {");
        pw.println("  accessFlags: " + Hex.u2(accessFlags));
        pw.println("  superclass: " + superclass);
        pw.println("  interfaces: " +
                ((interfaces == null) ? "<none>" : interfaces));
        pw.println("  sourceFile: " +
                ((sourceFile == null) ? "<none>" : sourceFile.toQuoted()));

        classData.debugPrint(out, verbose);
        annotationsDirectory.debugPrint(pw);
        
        pw.println("}");
    }
}