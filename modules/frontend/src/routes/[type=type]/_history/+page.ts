import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '../../../util.js';
import { transformBundle } from '../../../resource/resource-card.js';

export async function load({ fetch, params, url }) {
	const res = await fetch(`${base}/${params.type}/_history?${processParams(url.searchParams)}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(
			res.status as NumericRange<400, 599>,
			`error while loading the ${params.type} history bundle`
		);
	}

	return { bundle: await transformBundle(fetch, await res.json()) };
}
