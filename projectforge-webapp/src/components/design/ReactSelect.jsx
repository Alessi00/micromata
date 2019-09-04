import { faQuestion } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import PropTypes from 'prop-types';
import React from 'react';
import Select from 'react-select';
import makeAnimated from 'react-select/animated';
import AsyncCreatable from 'react-select/async-creatable';
import { UncontrolledTooltip } from 'reactstrap';
import revisedRandomId from '../../utilities/revisedRandomId';
import AdditionalLabel from './input/AdditionalLabel';
import style from './input/Input.module.scss';

function ReactSelect(
    {
        additionalLabel,
        getOptionLabel,
        label,
        labelProperty,
        loadOptions,
        required,
        tooltip,
        translations,
        value,
        valueProperty,
        values,
        ...props
    },
) {
    let Tag = Select;
    let defaultOptions;
    let options;

    if (loadOptions) {
        Tag = AsyncCreatable;
        if (values && values.length > 0) {
            // values are now the default options for the drop down without autocompletion call.
            defaultOptions = values;
        } else {
            defaultOptions = true;
        }
    } else {
        options = values;
    }

    let tooltipElement;
    if (tooltip) {
        const tooltipId = `rs-${revisedRandomId()}`;
        tooltipElement = (
            <React.Fragment>
                <span>{' '}</span>
                <FontAwesomeIcon
                    icon={faQuestion}
                    className={style.icon}
                    size="sm"
                    id={tooltipId}
                    style={{ color: 'gold' }}
                />
                <UncontrolledTooltip placement="right" target={tooltipId}>
                    {tooltip}
                </UncontrolledTooltip>
            </React.Fragment>
        );
    }
    return (
        <React.Fragment>
            <span className={`mm-select ${style.text}`}>{label}</span>
            {tooltipElement}
            <Tag
                // closeMenuOnSelect={false}
                components={makeAnimated()}
                options={options}
                isClearable={!required}
                getOptionValue={option => (option[valueProperty])}
                getOptionLabel={getOptionLabel || (option => (option[labelProperty]))}
                loadOptions={loadOptions}
                defaultOptions={defaultOptions}
                placeholder={translations['select.placeholder']}
                cache={{}}
                value={value || null}
                {...props}
            />
            <AdditionalLabel title={additionalLabel} />
        </React.Fragment>
    );
}

ReactSelect.propTypes = {
    label: PropTypes.string.isRequired,
    additionalLabel: PropTypes.string,
    value: PropTypes.oneOfType([
        PropTypes.shape({}),
        PropTypes.arrayOf(PropTypes.shape({})),
    ]),
    defaultValue: PropTypes.oneOfType([
        PropTypes.shape({}),
        PropTypes.arrayOf(PropTypes.shape({})),
    ]),
    values: PropTypes.arrayOf(PropTypes.object),
    valueProperty: PropTypes.string,
    labelProperty: PropTypes.string,
    multi: PropTypes.bool,
    required: PropTypes.bool,
    translations: PropTypes.shape({}).isRequired,
    loadOptions: PropTypes.func,
    getOptionLabel: PropTypes.func,
    onChange: PropTypes.func,
    className: PropTypes.string,
    tooltip: PropTypes.string,
};

ReactSelect.defaultProps = {
    values: undefined,
    value: undefined,
    defaultValue: undefined,
    additionalLabel: undefined,
    valueProperty: 'value',
    labelProperty: 'label',
    multi: false,
    required: false,
    loadOptions: undefined,
    getOptionLabel: undefined,
    onChange: undefined,
    className: undefined,
    tooltip: undefined,
};
export default ReactSelect;
