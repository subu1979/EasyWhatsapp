package com.subu1979.imagesender.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.subu1979.imagesender.R
import com.subu1979.imagesender.data.Country
import com.subu1979.imagesender.data.CountryRepository

/**
 * Full country/territory list with search by name, ISO alpha-2/alpha-3 or dialing code (FR-01).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    countries: List<Country>,
    selectedIso2: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(countries, query) { CountryRepository.filter(countries, query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.choose_country),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_country)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.heightIn(max = 560.dp)) {
            items(items = filtered, key = { it.iso2 }) { country ->
                ListItem(
                    headlineContent = { Text(text = "${country.flag}  ${country.name}") },
                    supportingContent = { Text(text = country.dialCodeText) },
                    trailingContent = {
                        RadioButton(
                            selected = country.iso2 == selectedIso2,
                            onClick = { onSelect(country.iso2) }
                        )
                    },
                    modifier = Modifier.clickable { onSelect(country.iso2) }
                )
            }
            item { Column(modifier = Modifier.height(24.dp)) {} }
        }
    }
}
