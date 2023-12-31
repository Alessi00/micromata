import PropTypes from 'prop-types';
import React from 'react';
import FavoritesPanel from '../../../../../containers/panel/favorite/FavoritesPanel';
import { getServiceURL, handleHTTPErrors } from '../../../../../utilities/rest';
import ReactSelect from '../../../../design/react-select/ReactSelect';
import { DynamicLayoutContext } from '../../context';
import DynamicValidationManager from '../input/DynamicValidationManager';

export const extractDataValue = (
    {
        data,
        id,
        labelProperty,
        multi,
        valueProperty,
        values,
    },
) => {
    let dataValue = Object.getByString(data, id);
    if (!multi && dataValue && values && values.length && values.length > 0) {
        // For react-select it seems to be important, that the current selected element matches
        // its value of the values list.
        const valueOfArray = (typeof dataValue === 'object') ? dataValue[valueProperty] : dataValue;
        const value = values.find((it) => it[valueProperty] === valueOfArray);

        if (value) {
            dataValue = value;
        }
    }

    if (typeof dataValue === 'string') {
        return {
            [labelProperty || 'displayName']: dataValue,
            [valueProperty || 'id']: dataValue,
        };
    }

    return dataValue;
};

function DynamicReactSelect(props) {
    const { data, setData, ui } = React.useContext(DynamicLayoutContext);
    const {
        id,
        favorites,
        autoCompletion,
        labelProperty,
        valueProperty,
        values,
    } = props;

    const value = extractDataValue({ data, ...props });

    const autoCompletionData = {};

    if (autoCompletion && autoCompletion.urlparams) {
        Object.keys(autoCompletion.urlparams).forEach((key) => {
            autoCompletionData[key] = Object.getByString(data, autoCompletion.urlparams[key]);
        });
    }

    return React.useMemo(() => {
        const onChange = (newValue) => {
            if (autoCompletion && autoCompletion.type) {
                setData({ [id]: newValue });
                return;
            }

            setData({ [id]: (newValue || {})[valueProperty] });
        };

        const onFavoriteSelect = (favoriteId, name) => {
            const newValue = {
                [valueProperty]: favoriteId,
                [labelProperty]: name,
            };
            onChange(newValue);
        };

        const loadOptions = (search, callback) => fetch(
            getServiceURL(
                autoCompletion.url.replace(':search', encodeURIComponent(search)),
                autoCompletionData,
            ),
            {
                method: 'GET',
                credentials: 'include',
                headers: {
                    Accept: 'application/json',
                },
            },
        )
            .then(handleHTTPErrors)
            .then((response) => response.json())
            .then(callback);

        const url = autoCompletion ? autoCompletion.url : undefined;

        let favoritesElement;
        if (favorites && favorites.length > 0) {
            favoritesElement = (
                <FavoritesPanel
                    onFavoriteSelect={onFavoriteSelect}
                    favorites={favorites}
                    translations={ui.translations}
                    htmlId={`dynamicFavoritesPopover-${ui.uid}-${id}`}
                />
            );
        }

        return (
            <DynamicValidationManager id={id}>
                <ReactSelect
                    className="invalid"
                    onChange={onChange}
                    translations={ui.translations}
                    {...props}
                    value={value}
                    loadOptions={(url && url.length > 0) ? loadOptions : undefined}
                />
                {favoritesElement}
            </DynamicValidationManager>
        );
    }, [data[id], value, setData, values, autoCompletionData]);
}

DynamicReactSelect.propTypes = {
    id: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    values: PropTypes.arrayOf(PropTypes.shape({})),
    favorites: PropTypes.arrayOf({}),
    additionalLabel: PropTypes.string,
    autoCompletion: PropTypes.shape({
        url: PropTypes.string,
        urlparams: PropTypes.shape({}),
    }),
    className: PropTypes.string,
    getOptionLabel: PropTypes.func,
    labelProperty: PropTypes.string,
    loadOptions: PropTypes.func,
    multi: PropTypes.bool,
    required: PropTypes.bool,
    valueProperty: PropTypes.string,
};

DynamicReactSelect.defaultProps = {
    value: undefined,
    favorites: undefined,
    additionalLabel: undefined,
    className: undefined,
    getOptionLabel: undefined,
    labelProperty: 'label',
    loadOptions: undefined,
    multi: false,
    required: false,
    valueProperty: 'value',
};

export default DynamicReactSelect;
