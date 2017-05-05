/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.examples.nativescript.script;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.script.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import org.elasticsearch.common.io.PathUtils;

/**
 * Score a document based on user preferences
 */
@SuppressForbidden(reason = "get path not configured in environment")
public class UserFeaturesLookupScript extends AbstractDoubleSearchScript {

    private static Float[] getUserFeatures(String line) {
        Scanner scanner = new Scanner(line);
        ArrayList<Float> floats = new ArrayList<>();
        while (scanner.hasNextFloat()) {
            floats.add(scanner.nextFloat());
        }
        return floats.toArray(new Float[0]);
    }

    static Float[][] userFeatures = null;

    private static void loadUserFeatures() {
        String userfeaturesFileName = "/usr/share/elasticsearch/user-features.csv";

        List<String> lines = null;
        try {
            lines = Files.readAllLines(PathUtils.get(userfeaturesFileName), Charset.forName("UTF-8"));
            userFeatures = new Float[lines.size()][200];
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Float[] features = getUserFeatures(line);
                userFeatures[i] = features;
            }
        } catch (IOException e) {
            throw new ScriptException("Could not load user features: ", e);
        }

    }

    static {
        loadUserFeatures();
    }


    final static public String SCRIPT_NAME = "user_preference";


    /**
     * Native scripts are build using factories that are registered in the
     * {@link org.elasticsearch.examples.nativescript.plugin.NativeScriptExamplesPlugin#onModule(org.elasticsearch.script.ScriptModule)}
     * method when plugin is loaded.
     */
    public static class Factory implements NativeScriptFactory {

        /**
         * This method is called for every search on every shard.
         *
         * @param params list of script parameters passed with the query
         * @return new native script
         */
        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) {
            // The XContentMapValues helper class can be used to simplify parameter parsing
            String productFeaturesfieldName = params == null ? null : XContentMapValues.nodeStringValue(params.get("field"), null);
            String indexfieldName = params == null ? null : XContentMapValues.nodeStringValue(params.get("index"), null);
            String userId = params == null ? null : XContentMapValues.nodeStringValue(params.get("user"), null);

            if (productFeaturesfieldName == null || indexfieldName == null || userId == null) {
                throw new ScriptException("field and index and user parameters are required!");
            }

            return new UserFeaturesLookupScript(productFeaturesfieldName, userId);
        }

        /**
         * Indicates if document scores may be needed by the produced scripts.
         *
         * @return {@code true} if scores are needed.
         */
        @Override
        public boolean needsScores() {
            return false;
        }

    }

    private final String productFeaturesfieldName;
    //private final String indexfieldName;
    private final Integer userId;

    /**
     * Factory creates this script on every
     *
     * @param productFeaturesfieldName the name of the field that should be checked
     */
    private UserFeaturesLookupScript(String productFeaturesfieldName, String userId) {
        this.productFeaturesfieldName = productFeaturesfieldName;
        this.userId = Integer.parseInt(userId);
    }

    @Override
    public double runAsDouble() {
        ScriptDocValues docValue = (ScriptDocValues) doc().get(this.productFeaturesfieldName);
        List<Double> productFeature = ((ScriptDocValues.Doubles) docValue).getValues();

        Double sum = 0.0;
        for (int i = 0; i < productFeature.size(); i++) {
            sum += productFeature.get(i) * userFeatures[userId][i];
        }

        //System.out.println(productFeature);
        //System.out.println(userFeatures[userId]);

        return sum;
    }
}
