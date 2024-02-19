import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '../../util.js';
import { transformBundle } from '../../resource/resource-card.js';

export async function load({ fetch, url }) {
	const res = await fetch(`${base}/_history?${processParams(url.searchParams)}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, {
			message: 'An error happened while loading the history. Please try again later.'
		});
	}

	return { bundle: await transformBundle(fetch, await res.json()) };
}
