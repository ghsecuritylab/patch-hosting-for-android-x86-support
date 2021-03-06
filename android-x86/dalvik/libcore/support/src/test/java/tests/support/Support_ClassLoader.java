/*
 * Copyright (C) 2009 The Android Open Source Project
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
package tests.support;

import java.net.URL;
import java.net.URLClassLoader;

import dalvik.system.PathClassLoader;

/**
 * Support class for creating a file-based ClassLoader. Delegates to either
 * Dalvik's PathClassLoader or the RI's URLClassLoader, but does so by-name.
 * This allows us to run corresponding tests in both environments.
 */
public abstract class Support_ClassLoader {

    public abstract ClassLoader getClassLoader(URL url, ClassLoader parent);
    
    public static ClassLoader getInstance(URL url, ClassLoader parent) {
        try {
            Support_ClassLoader factory; 
            
            if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
                factory = (Support_ClassLoader)Class.forName(
                    "tests.support.Support_ClassLoader$Dalvik").newInstance();
            } else {
                factory = (Support_ClassLoader)Class.forName(
                    "tests.support.Support_ClassLoader$RefImpl").newInstance();
            }
            
            return factory.getClassLoader(url, parent);
        } catch (Exception ex) {
            throw new RuntimeException("Unable to create ClassLoader", ex);
        }
    }
    
    static class Dalvik extends Support_ClassLoader {
        public ClassLoader getClassLoader(URL url, ClassLoader parent) {
            return new PathClassLoader(url.getPath(), parent);
        }
    }
    
    static class RefImpl extends Support_ClassLoader {
        public ClassLoader getClassLoader(URL url, ClassLoader parent) {
            return new URLClassLoader(new URL[] { url }, parent);
        }
    }
}
