/*
 This file is part of SmileMeBack.

 SmileMeBack is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 SmileMeBack is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with SmileMeBack.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.smilemeback.storage;

import android.util.Log;

import com.google.common.base.Optional;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class representing a list of categories.
 */
public class Categories implements Iterable<Category> {
    private static String TAG = Categories.class.getCanonicalName();

    private final File parent;
    private final List<Category> categories = new ArrayList<>();


    public Categories(final File parent) throws StorageException {
        super();
        this.parent = parent;

        if (!parent.isDirectory()) {
            throw new IllegalArgumentException("Parent folder <" + parent.getName() + "> not a directory!");
        }

        parseCategories();
    }

    @Override
    public Iterator<Category> iterator() {
        return categories.iterator();
    }

    public Category get(int position) {
        return categories.get(position);
    }

    public int size() {
        return categories.size();
    }

    public File getParent() {
        return parent;
    }

    private void parseCategories() throws StorageException {
        categories.clear();
        // Parse all directories as categories.
        for (File folder : parent.listFiles()) {
            if (folder.isDirectory()) {
                categories.add(new Category(folder));
            } else {
                throw new StorageException("Non-directory in categories folder <" + folder.getAbsolutePath() + ">");
            }
        }
        Collections.sort(categories);
        // check that the category indices correspond to their positions in the list
        for (int idx=0 ; idx<categories.size() ; ++idx) {
            Category category = categories.get(idx);
            if (category.getPosition() != idx) {
                String err = "Category <" + category + "> position in name <" + category.getPosition() + "> does not correspond to position in category list <" + idx + ">";
                throw new StorageException(err);
            }
        }
    }

    /**
     * Delete all categories.
     * @throws StorageException
     */
    public void truncate() throws StorageException {
        Log.d(TAG, "Truncating all categories");
        try {
            FileUtils.deleteDirectory(parent);
            FileUtils.forceMkdir(parent);
            categories.clear();
            parseCategories();
        } catch (IOException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    /**
     * Add a new category, given its {@link com.smilemeback.storage.Name} and a
     * {@link java.io.InputStream} resource to its thumbnail image.
     *
     * @param name The name of the category
     * @param thumbnailStream The input stream containing the thumbnail.
     * @throws StorageException
     */
    public Category add(final Name name, final InputStream thumbnailStream) throws StorageException {
        Log.d(TAG, "Adding empty category with name <" + name + ">");
        File folder = new File(
                parent,
                StorageNameUtils.constructCategoryFileName(size(), name));
        // create category and add thumbnail directory.
        try {
            FileUtils.forceMkdir(folder);
            File thumbNailFile = new File(folder, Category.THUMBNAIL);
            FileUtils.copyInputStreamToFile(thumbnailStream, thumbNailFile);
            Category category = new Category(folder);
            categories.add(category);
            return category;
        } catch (IOException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            try {
                thumbnailStream.close();
            } catch (IOException e) {
                throw new StorageException(e.getMessage(), e);
            }
        }
    }

    public void delete(final Collection<Category> selection) throws StorageException {
        if (!categories.containsAll(selection)) {
            throw new StorageException("Selection contains categories not in storage!");
        }
        try {
            for (Category category : selection) {
                category.delete();
            }
        } catch (StorageException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            organize();
        }
    }

    public void organize() throws StorageException {
        Log.i(TAG, "Organizing folder <" + parent + ">");
        try {
            FileUtils.forceMkdir(parent);
            List<Category> categories = new ArrayList<>();
            for (File file : parent.listFiles()) {
                if (file.isFile()) {
                    Log.d(TAG, "Deleting file <" + file + ">");
                    FileUtils.deleteQuietly(file);
                } else if (file.isDirectory()) {
                    try {
                        categories.add(new Category(file));
                    } catch (StorageException e) {
                        Log.d(TAG, "Could not initialize <" + file + ">, now trying to delete it!");
                        Log.d(TAG, e.getMessage());
                        FileUtils.deleteDirectory(file);
                    }
                }
            }

            Collections.sort(categories);
            int nextPos = 0;
            for (Category category : categories) {
                File newFolder = new File(
                        parent,
                        StorageNameUtils.constructCategoryFileName(nextPos, category.getName()));
                Log.d(TAG, "Moving <" + category.getFolder() + "> to <" + newFolder + ">");
                FileUtils.moveDirectory(category.getFolder(), newFolder);
                nextPos += 1;
            }

        } catch (IOException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            parseCategories();
        }
    }
}
