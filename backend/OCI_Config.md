OCI_CONFIG_FILE = os.path.expanduser("~/.oci/config")
 
# Which profile section inside that config file to use (usually "DEFAULT").
OCI_CONFIG_PROFILE = "DEFAULT"
 
# >>> FILL THIS IN: OCID of the compartment your Generative AI resources
# live in. Find it in the OCI Console under Identity > Compartments.
OCI_COMPARTMENT_ID = "ocid1.tenancy.oc1..aaaaaaaay3xbazubrpauckm2fdthuvl3glxuwfojyl76eovqce6ek3xc2rlq"
 
# >>> FILL THIS IN: the region-specific Generative AI endpoint. Replace
# "us-chicago-1" with your actual region (check the region= line in your
# ~/.oci/config file - it must match).
OCI_ENDPOINT = "https://inference.generativeai.ap-hyderabad-1.oci.oraclecloud.com"
 
# >>> FILL THIS IN: the model OCID to use for chat. Find it in the OCI
# Console under Generative AI > Model Catalog > click a model > copy its OCID.
OCI_MODEL_ID = "ocid1.generativeaimodel.oc1.ap-hyderabad-1.amaaaaaask7dceyaaccktjkitpfn3zp3xnkg6yclc6izeahggh2hkwawfjna"
 
# >>> CHECK THIS MATCHES YOUR MODEL: "generic" for Meta/Llama-family models,
# "cohere" for Cohere Command-family models. Must match whichever model
# OCI_MODEL_ID points to - the two use different request formats.
OCI_MODEL_FAMILY = "cohere"