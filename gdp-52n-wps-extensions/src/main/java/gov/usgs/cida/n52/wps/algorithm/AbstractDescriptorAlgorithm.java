package gov.usgs.cida.n52.wps.algorithm;

import gov.usgs.cida.n52.wps.algorithm.descriptor.AlgorithmDescriptor;
import gov.usgs.cida.n52.wps.algorithm.descriptor.ComplexDataInputDescriptor;
import gov.usgs.cida.n52.wps.algorithm.descriptor.ComplexDataOutputDescriptor;
import gov.usgs.cida.n52.wps.algorithm.descriptor.InputDescriptor;
import gov.usgs.cida.n52.wps.algorithm.descriptor.LiteralDataInputDescriptor;
import gov.usgs.cida.n52.wps.algorithm.descriptor.LiteralDataOutputDescriptor;
import gov.usgs.cida.n52.wps.algorithm.descriptor.OutputDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.opengis.ows.x11.AllowedValuesDocument.AllowedValues;
import net.opengis.wps.x100.ComplexDataCombinationType;
import net.opengis.wps.x100.ComplexDataCombinationsType;
import net.opengis.wps.x100.ComplexDataDescriptionType;
import net.opengis.wps.x100.InputDescriptionType;
import net.opengis.wps.x100.LiteralInputType;
import net.opengis.wps.x100.OutputDescriptionType;
import net.opengis.wps.x100.ProcessDescriptionType;
import net.opengis.wps.x100.ProcessDescriptionType.DataInputs;
import net.opengis.wps.x100.ProcessDescriptionType.ProcessOutputs;
import net.opengis.wps.x100.ProcessDescriptionsDocument;
import net.opengis.wps.x100.ProcessDescriptionsDocument.ProcessDescriptions;
import net.opengis.wps.x100.SupportedComplexDataInputType;
import net.opengis.wps.x100.SupportedComplexDataType;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlValidationError;
import org.n52.wps.io.GeneratorFactory;
import org.n52.wps.io.IGenerator;
import org.n52.wps.io.IOHandler;
import org.n52.wps.io.IParser;
import org.n52.wps.io.ParserFactory;
import org.n52.wps.io.data.IData;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.observerpattern.IObserver;
import org.n52.wps.server.observerpattern.ISubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDescriptorAlgorithm implements IAlgorithm, ISubject {

    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractDescriptorAlgorithm.class);
    
    private AlgorithmDescriptor descriptor;
    private ProcessDescriptionType description;
    
    public AbstractDescriptorAlgorithm() {
        super();
    }

    @Override
    public synchronized ProcessDescriptionType getDescription() {
        if (description == null) {
            description = createProcessDescription();
        }
        return description;
    }

    @Override
    public String getWellKnownName() {
        return getAlgorithmDescriptor().getIdentifier();
    }

    private ProcessDescriptionType createProcessDescription() {

        AlgorithmDescriptor algorithmDescriptor = getAlgorithmDescriptor();

        ProcessDescriptionsDocument document = ProcessDescriptionsDocument.Factory.newInstance();
        ProcessDescriptions processDescriptions = document.addNewProcessDescriptions();
        ProcessDescriptionType processDescription = processDescriptions.addNewProcessDescription();

        if (algorithmDescriptor == null) {
            throw new IllegalStateException("Instance must have an algorithm descriptor");
        } else {

            // 1. Identifier
            processDescription.setStatusSupported(algorithmDescriptor.getStatusSupported());
            processDescription.setStoreSupported(algorithmDescriptor.getStoreSupported());
            processDescription.setProcessVersion(algorithmDescriptor.getVersion());
            processDescription.addNewIdentifier().setStringValue(algorithmDescriptor.getIdentifier());
            processDescription.addNewTitle().setStringValue( algorithmDescriptor.hasTitle() ?
                    algorithmDescriptor.getTitle() :
                    algorithmDescriptor.getIdentifier());
            if (algorithmDescriptor.hasAbstract()) {
                processDescription.addNewAbstract().setStringValue(algorithmDescriptor.getAbstract());
            }

            // 2. Inputs
            Collection<InputDescriptor> inputDescriptors = algorithmDescriptor.getInputDescriptors();
            DataInputs dataInputs = null;
            if (inputDescriptors.size() > 0) {
                dataInputs = processDescription.addNewDataInputs();
            }
            for (InputDescriptor inputDescriptor : inputDescriptors) {

                InputDescriptionType dataInput = dataInputs.addNewInput();
                dataInput.setMinOccurs(inputDescriptor.getMinOccurs());
                dataInput.setMaxOccurs(inputDescriptor.getMaxOccurs());

                dataInput.addNewIdentifier().setStringValue(inputDescriptor.getIdentifier());
                dataInput.addNewTitle().setStringValue( inputDescriptor.hasTitle() ?
                        inputDescriptor.getTitle() :
                        inputDescriptor.getIdentifier());
                if (inputDescriptor.hasAbstract()) {
                    dataInput.addNewAbstract().setStringValue(inputDescriptor.getAbstract());
                }

                if (inputDescriptor instanceof LiteralDataInputDescriptor) {
                    LiteralDataInputDescriptor<?> literalDescriptor = (LiteralDataInputDescriptor)inputDescriptor;

                    LiteralInputType literalData = dataInput.addNewLiteralData();
                    literalData.addNewDataType().setReference(literalDescriptor.getDataType());

                    if (literalDescriptor.hasDefaultValue()) {
                        literalData.setDefaultValue(literalDescriptor.getDefaultValue());
                    }
                    if (literalDescriptor.hasAllowedValues()) {
                        AllowedValues allowed = literalData.addNewAllowedValues();
                        for (String allowedValue : literalDescriptor.getAllowedValues()) {
                            allowed.addNewValue().setStringValue(allowedValue);
                        }
                    } else {
                        literalData.addNewAnyValue();
                    }

                } else if (inputDescriptor instanceof ComplexDataInputDescriptor) {
                    SupportedComplexDataInputType complexDataType = dataInput.addNewComplexData();
                    ComplexDataInputDescriptor complexInputDescriptor =
                            (ComplexDataInputDescriptor)inputDescriptor;
                    if (complexInputDescriptor.hasMaximumMegaBytes()) {
                        complexDataType.setMaximumMegabytes(complexInputDescriptor.getMaximumMegaBytes());
                    }
                    describeComplexDataInputType(complexDataType, inputDescriptor.getBinding());
                }
            }

            // 3. Outputs
            ProcessOutputs dataOutputs = processDescription.addNewProcessOutputs();
            Collection<OutputDescriptor> outputDescriptors = algorithmDescriptor.getOutputDescriptors();
            if (outputDescriptors.size() < 1) {
               LOGGER.error("No outputs found for algorithm {}", algorithmDescriptor.getIdentifier());
            }
            for (OutputDescriptor outputDescriptor : outputDescriptors) {

                OutputDescriptionType dataOutput = dataOutputs.addNewOutput();
                dataOutput.addNewIdentifier().setStringValue(outputDescriptor.getIdentifier());
                dataOutput.addNewTitle().setStringValue( outputDescriptor.hasTitle() ?
                        outputDescriptor.getTitle() :
                        outputDescriptor.getIdentifier());
                if (outputDescriptor.hasAbstract()) {
                    dataOutput.addNewAbstract().setStringValue(outputDescriptor.getAbstract());
                }

                if (outputDescriptor instanceof LiteralDataOutputDescriptor) {
                    LiteralDataOutputDescriptor<?> literalDescriptor = (LiteralDataOutputDescriptor)outputDescriptor;
                    dataOutput.addNewLiteralOutput().addNewDataType().
                            setReference(literalDescriptor.getDataType());
                } else if (outputDescriptor instanceof ComplexDataOutputDescriptor) {
                    describeComplexDataOutputType(dataOutput.addNewComplexOutput(), outputDescriptor.getBinding());
               }
            }
        }
        return document.getProcessDescriptions().getProcessDescriptionArray(0);
    }

    private void describeComplexDataInputType(SupportedComplexDataType complexData, Class dataTypeClass) {
        List<IParser> parsers = ParserFactory.getInstance().getAllParsers();
        List<IParser> foundParsers = new ArrayList<IParser>();
        for (IParser parser : parsers) {
            Class[] supportedClasses = parser.getSupportedInternalOutputDataType();
            for (Class clazz : supportedClasses) {
                if (dataTypeClass.isAssignableFrom(clazz)) {
                    foundParsers.add(parser);
                }
            }
        }
        describeComplexDataType(complexData, foundParsers);
    }

    private void describeComplexDataOutputType(SupportedComplexDataType complexData, Class dataTypeClass) {

        List<IGenerator> generators = GeneratorFactory.getInstance().getAllGenerators();
        List<IGenerator> foundGenerators = new ArrayList<IGenerator>();
        for (IGenerator generator : generators) {
            Class[] supportedClasses = generator.getSupportedInternalInputDataType();
            for (Class clazz : supportedClasses) {
                if (clazz.isAssignableFrom(dataTypeClass)) {
                    foundGenerators.add(generator);
                }
            }
        }
        describeComplexDataType(complexData, foundGenerators);
    }

    private void describeComplexDataType(
            SupportedComplexDataType complexData,
            List<? extends IOHandler> handlers)
    {
        ComplexDataCombinationType defaultFormatType = complexData.addNewDefault();
        ComplexDataCombinationsType supportedFormatType = complexData.addNewSupported();

        int formatCount = 0;
        for (IOHandler generator : handlers) {

            String[] formats = generator.getSupportedFormats();
            String[] encodings = generator.getSupportedEncodings();
            String[] schemas = generator.getSupportedSchemas();

            // if formats, encodings or schemas arrays are 'null' or empty, create
            // new array with single 'null' element.  We do this so we can utilize
            // a single set of nested loops to process all permutations.  'null'
            // values will not be output...
            if (formats == null || formats.length == 0) {
                formats = new String[] { null }; 
            }
            if (encodings == null || encodings.length == 0) {
                encodings = new String[] { null };
            }
            if (schemas == null || schemas.length == 0) {
                schemas = new String[] { null };
            }
            
            for (String format : formats) {
                for (String encoding : encodings) {
                    for (String schema : schemas) {
                        if(formatCount++ == 0) {
                            describeComplexDataFormat(
                                    defaultFormatType.addNewFormat(),
                                    format, encoding, schema);
                        }
                        describeComplexDataFormat(
                                supportedFormatType.addNewFormat(),
                                format, encoding, schema);
                    }
                }
            }
        }
    }
    
    public void describeComplexDataFormat(
            ComplexDataDescriptionType description,
            String format,
            String encoding,
            String schema)
    {
        if (format != null) {
            description.setMimeType(format);
        }
        if (encoding != null) {
            description.setEncoding(encoding);
        }
        if (schema != null) {
            description.setSchema(schema);
        }
    }

    @Override
    public boolean processDescriptionIsValid() {
        XmlOptions xmlOptions = new XmlOptions();
        List<XmlValidationError> xmlValidationErrorList = new ArrayList<XmlValidationError>();
            xmlOptions.setErrorListener(xmlValidationErrorList);
        boolean valid = getDescription().validate(xmlOptions);
        if (!valid) {
            LOGGER.error("Error validating process description for " + getClass().getCanonicalName());
            for (XmlValidationError xmlValidationError : xmlValidationErrorList) {
                LOGGER.error("\tMessage: {}", xmlValidationError.getMessage());
                LOGGER.error("\tLocation of invalid XML: {}",
                     xmlValidationError.getCursorLocation().xmlText());
            }
        }
        return valid;
    }

    protected final synchronized AlgorithmDescriptor getAlgorithmDescriptor() {
        if (descriptor == null) {
            descriptor = createAlgorithmDescriptor();
        }
        return descriptor;
    }
    
    protected abstract AlgorithmDescriptor createAlgorithmDescriptor();

    @Override
    public Class<? extends IData> getInputDataType(String identifier) {
        AlgorithmDescriptor algorithmDescriptor = getAlgorithmDescriptor();
        if (algorithmDescriptor != null) {
            return getAlgorithmDescriptor().getInputDescriptor(identifier).getBinding();
        } else {
            throw new IllegalStateException("Instance must have an algorithm descriptor");
        }
    }

    @Override
    public Class<? extends IData> getOutputDataType(String identifier) {
        AlgorithmDescriptor algorithmDescriptor = getAlgorithmDescriptor();
        if (algorithmDescriptor != null) {
            return getAlgorithmDescriptor().getOutputDescriptor(identifier).getBinding();
        } else {
            throw new IllegalStateException("Instance must have an algorithm descriptor");
        }
    }

    private List observers = new ArrayList();
    private Object state = null;

    @Override
    public Object getState() {
        return state;
    }

    @Override
    public void update(Object state) {
        this.state = state;
        notifyObservers();
    }

    @Override
    public void addObserver(IObserver o) {
        observers.add(o);
    }

    @Override
    public void removeObserver(IObserver o) {
        observers.remove(o);
    }

    public void notifyObservers() {
        Iterator i = observers.iterator();
        while (i.hasNext()) {
            IObserver o = (IObserver) i.next();
            o.update(this);
        }
    }

    List<String> errorList = new ArrayList();
    protected List<String> addError(String error) {
        errorList.add(error);
        return errorList;
    }

    @Override
    public List<String> getErrors() {
        return errorList;
    }
}